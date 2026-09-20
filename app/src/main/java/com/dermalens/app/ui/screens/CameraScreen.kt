package com.dermalens.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import android.util.Rational
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.dermalens.app.ml.decodeUprightBitmap
import com.dermalens.app.navigation.Screen
import java.util.concurrent.Executors
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Crops [sourceUri] to exactly what's visible inside the guide box, given how the user has
 * panned/zoomed it. Mirrors the same transform the UI applies (ContentScale.Fit base fit,
 * centered, then the user's additional scale/offset) as an android.graphics.Matrix, inverts it,
 * and maps the guide box's screen corners back into the original bitmap's pixel space.
 */
private fun cropGalleryImageToFrame(
    context: android.content.Context,
    sourceUri: android.net.Uri,
    containerWidthPx: Float,
    containerHeightPx: Float,
    guideBoxSizePx: Float,
    userScale: Float,
    userOffsetX: Float,
    userOffsetY: Float
): android.net.Uri? {
    return try {
        // decodeUprightBitmap, not a bare BitmapFactory decode: the preview below is drawn by
        // Coil, which applies EXIF orientation. Decoding without it here meant this function
        // inverted the display matrix against pixels rotated 90 degrees from the ones the user
        // was actually looking at, so the crop landed on the wrong part of the photo entirely.
        val bitmap = decodeUprightBitmap(context, sourceUri) ?: return null
        val bitmapW = bitmap.width.toFloat()
        val bitmapH = bitmap.height.toFloat()

        val baseScale = minOf(containerWidthPx / bitmapW, containerHeightPx / bitmapH)
        val cx = containerWidthPx / 2f
        val cy = containerHeightPx / 2f

        val matrix = Matrix()
        matrix.postScale(baseScale, baseScale)
        matrix.postTranslate(cx - bitmapW * baseScale / 2f, cy - bitmapH * baseScale / 2f)
        matrix.postScale(userScale, userScale, cx, cy)
        matrix.postTranslate(userOffsetX, userOffsetY)

        val inverse = Matrix()
        if (!matrix.invert(inverse)) return null

        val half = guideBoxSizePx / 2f
        val corners = floatArrayOf(
            cx - half, cy - half,
            cx + half, cy - half,
            cx + half, cy + half,
            cx - half, cy + half
        )
        inverse.mapPoints(corners)

        val xs = floatArrayOf(corners[0], corners[2], corners[4], corners[6])
        val ys = floatArrayOf(corners[1], corners[3], corners[5], corners[7])
        val left = (xs.min().coerceIn(0f, bitmapW)).toInt()
        val top = (ys.min().coerceIn(0f, bitmapH)).toInt()
        val right = (xs.max().coerceIn(0f, bitmapW)).toInt()
        val bottom = (ys.max().coerceIn(0f, bitmapH)).toInt()
        val cropW = (right - left).coerceAtLeast(1)
        val cropH = (bottom - top).coerceAtLeast(1)

        writeCropToCache(context, Bitmap.createBitmap(bitmap, left, top, cropW, cropH))
    } catch (e: Exception) {
        Log.e("DermaLens", "Gallery image crop failed", e)
        null
    }
}

/**
 * Crops a freshly captured camera photo down to just what was inside the guide box.
 *
 * Without this the guide box was decorative on the camera path: the gallery path cropped to the
 * frame but `capturePhoto` handed the full-frame JPEG straight to the result screen. The guide
 * box is a 340.dp square on a full-screen preview, so it covers roughly a third of the frame's
 * area -- meaning the model was being shown a lesion at a smaller linear scale than anything in
 * the training set, which is all lesion-filling crops. That alone is enough to sink confidence on
 * a model that validates fine, and it also made camera and gallery scans of the same skin
 * disagree.
 *
 * Sized at 340.dp (not the original 260.dp) after a live A/B test on one wart photo (2026-09-21):
 * a tight crop scored 8.0% (below the confidence floor, wrong condition), a moderate crop scored
 * 55.8% (correct), and the widest, most-context crop scored 76.3% (correct) -- monotonically
 * better with more surrounding skin in frame, consistent with the over-zoom finding in the
 * README. 340.dp is a deliberate move toward more context, not a re-derivation of the exact
 * optimum -- re-test if a case shows the opposite (bigger frame hurting confidence).
 *
 * Assumes the captured image covers the same field of view as the preview, which is what the
 * [ViewPort] set in `startCamera` guarantees -- PreviewView's default FILL_CENTER scale type
 * otherwise shows less than the sensor captures, and these fractions would crop a wider region
 * than the user framed.
 */
private fun cropCameraImageToFrame(
    context: android.content.Context,
    sourceUri: android.net.Uri,
    containerWidthPx: Float,
    containerHeightPx: Float,
    guideBoxSizePx: Float
): android.net.Uri? {
    return try {
        if (containerWidthPx <= 0f || containerHeightPx <= 0f) return null
        val bitmap = decodeUprightBitmap(context, sourceUri) ?: return null

        // The guide box is centered, so express it as a fraction of the container and apply the
        // same fraction to the image -- no dependence on the capture's absolute resolution.
        val halfFractionX = (guideBoxSizePx / 2f) / containerWidthPx
        val halfFractionY = (guideBoxSizePx / 2f) / containerHeightPx

        val left = ((0.5f - halfFractionX) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = ((0.5f - halfFractionY) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = ((0.5f + halfFractionX) * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ((0.5f + halfFractionY) * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)

        writeCropToCache(context, Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top))
    } catch (e: Exception) {
        Log.e("DermaLens", "Camera image crop failed", e)
        null
    }
}

/** Saves a cropped bitmap as a JPEG in the cache dir and returns its file:// Uri. */
private fun writeCropToCache(context: android.content.Context, cropped: Bitmap): android.net.Uri {
    val file = java.io.File(context.cacheDir, "scan_crop_${System.currentTimeMillis()}.jpg")
    java.io.FileOutputStream(file).use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 92, out) }
    return android.net.Uri.fromFile(file)
}

@Composable
fun ScanScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    // Once the user denies without a rationale being showable again, Android won't re-prompt --
    // the only way back in is the system Settings screen.
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted && activity != null) {
            permanentlyDenied = !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) CameraPreviewScreen(navController)
    else CameraPermissionDeniedScreen(
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        },
        permanentlyDenied = permanentlyDenied,
        navController = navController
    )
}

@Composable
fun CameraPreviewScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    var isFlashOn by remember { mutableStateOf(false) }
    // Auto-opens once per camera visit unless the user has previously checked "Don't show
    // again" -- the info icon still reopens it manually regardless of that preference.
    var showConditionsInfo by remember { mutableStateOf(!prefs.getBoolean(DermaPrefs.KEY_HIDE_SCAN_CONDITIONS_INFO, false)) }
    var dontShowConditionsInfoAgain by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    // Pan/zoom for a gallery-picked image, so the user can position it within the guide frame
    // before scanning. Resets whenever a new image is picked.
    var galleryScale by remember { mutableFloatStateOf(1f) }
    var galleryOffsetX by remember { mutableFloatStateOf(0f) }
    var galleryOffsetY by remember { mutableFloatStateOf(0f) }
    var containerSizePx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val guideBoxSizePx = with(density) { 340.dp.toPx() }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            galleryScale = 1f
            galleryOffsetX = 0f
            galleryOffsetY = 0f
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    fun startCamera(frontCamera: Boolean = false, viewSize: IntSize = IntSize.Zero) {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(android.util.Size(1280, 1280))
                .build()
            val cameraSelector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                // Bind preview and capture as a group with a ViewPort matching the preview's
                // aspect ratio and scale type, so the saved JPEG covers the same field of view
                // the user is looking at. That's what lets cropCameraImageToFrame map the guide
                // box across by simple proportion. Without it, PreviewView's default FILL_CENTER
                // crops the preview while the capture keeps the wider sensor FOV, and the two
                // silently disagree about what "inside the frame" means.
                val useCaseGroup = UseCaseGroup.Builder()
                    .also { group ->
                        if (viewSize.width > 0 && viewSize.height > 0) {
                            group.setViewPort(
                                ViewPort.Builder(
                                    Rational(viewSize.width, viewSize.height),
                                    capture.targetRotation
                                ).setScaleType(ViewPort.FILL_CENTER).build()
                            )
                        }
                    }
                    .addUseCase(preview)
                    .addUseCase(capture)
                    .build()
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup)
                imageCapture = capture
            } catch (e: Exception) { Log.e("DermaLens", "Camera binding failed", e) }
        }, ContextCompat.getMainExecutor(context))
    }

    // Rebinds once the preview's real size is known (containerSizePx starts Zero and is filled in
    // by onSizeChanged), since the ViewPort above needs it to match the capture FOV to the preview.
    LaunchedEffect(isFrontCamera, containerSizePx) { startCamera(isFrontCamera, containerSizePx) }

    val scope = rememberCoroutineScope()

    fun capturePhoto(capture: ImageCapture, onCaptured: (android.net.Uri) -> Unit, onFailed: () -> Unit) {
        val photoFile = java.io.File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = android.net.Uri.fromFile(photoFile)
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onCaptured(uri) }
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("DermaLens", "Photo capture failed", exception)
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onFailed() }
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .onSizeChanged { containerSizePx = it }
    ) {

        if (selectedImageUri != null) {
            AsyncImage(
                model = selectedImageUri,
                contentDescription = "Selected image (pinch to zoom, drag to pan)",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = galleryScale,
                        scaleY = galleryScale,
                        translationX = galleryOffsetX,
                        translationY = galleryOffsetY
                    )
                    .pointerInput(selectedImageUri) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            galleryScale = (galleryScale * zoom).coerceIn(1f, 5f)
                            galleryOffsetX += pan.x
                            galleryOffsetY += pan.y
                        }
                    },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        } else {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val cam = camera ?: return@detectTransformGestures
                            val zoomState = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
                            val newRatio = (zoomState.zoomRatio * zoom)
                                .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                            cam.cameraControl.setZoomRatio(newRatio)
                        }
                    }
            )
        }

        // Dark overlay at top and bottom
        Box(modifier = Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.5f)))
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.6f)))

        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.size(42.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Skin Scan", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Position skin within frame", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { showConditionsInfo = true },
                    modifier = Modifier.size(42.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.Info, contentDescription = "What can this scan for?", tint = Color.White)
                }

                IconButton(
                    onClick = { isFlashOn = !isFlashOn; camera?.cameraControl?.enableTorch(isFlashOn) },
                    modifier = Modifier.size(42.dp).background(
                        if (isFlashOn) DermaGreen else Color.White.copy(alpha = 0.15f), CircleShape
                    )
                ) {
                    Icon(
                        if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }
            }
        }

        if (showConditionsInfo) {
            AlertDialog(
                onDismissRequest = { showConditionsInfo = false },
                icon = { Icon(Icons.Default.Info, contentDescription = null, tint = DermaGreen) },
                title = { Text("What This Scan Can Detect", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "DermaLens currently recognizes 6 skin conditions:",
                            fontSize = 14.sp, color = Color(0xFF374151)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        listOf("Acne Vulgaris", "Eczema", "Melasma", "Tinea", "Warts", "Scabies").forEach { condition ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(DermaGreen))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(condition, fontSize = 14.sp, color = Color(0xFF1a1a1a))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "A photo of anything outside these -- another condition, or a non-skin object -- may still return a low-confidence or incorrect result. Always consult a dermatologist for an actual diagnosis.",
                            fontSize = 12.sp, color = Color(0xFF6B7280), lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { dontShowConditionsInfoAgain = !dontShowConditionsInfoAgain }
                        ) {
                            Checkbox(checked = dontShowConditionsInfoAgain, onCheckedChange = { dontShowConditionsInfoAgain = it }, colors = CheckboxDefaults.colors(checkedColor = DermaGreen))
                            Text("Don't show this again", fontSize = 13.sp, color = Color(0xFF374151))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showConditionsInfo = false
                        if (dontShowConditionsInfoAgain) {
                            prefs.edit().putBoolean(DermaPrefs.KEY_HIDE_SCAN_CONDITIONS_INFO, true).apply()
                        }
                    }) { Text("Got it", color = DermaGreen, fontWeight = FontWeight.Bold) }
                }
            )
        }

        // Scan Frame
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, DermaGreen, RoundedCornerShape(24.dp))
        ) {
            if (isScanning) {
                ScanningSweepEffect()
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isScanning) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = DermaGreen, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Analyzing skin...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flip camera
                IconButton(
                    onClick = { isFrontCamera = !isFrontCamera },
                    modifier = Modifier.size(52.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip", tint = Color.White, modifier = Modifier.size(24.dp))
                }

                // Capture button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(3.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(DermaGreen),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            val galleryUri = selectedImageUri
                            if (galleryUri != null) {
                                isScanning = true
                                scope.launch {
                                    val croppedUri = withContext(Dispatchers.IO) {
                                        cropGalleryImageToFrame(
                                            context, galleryUri,
                                            containerSizePx.width.toFloat(), containerSizePx.height.toFloat(),
                                            guideBoxSizePx, galleryScale, galleryOffsetX, galleryOffsetY
                                        )
                                    }
                                    isScanning = false
                                    navController.navigate(Screen.ScanResult.createRoute((croppedUri ?: galleryUri).toString()))
                                }
                            } else {
                                val capture = imageCapture
                                if (capture != null) {
                                    isScanning = true
                                    capturePhoto(
                                        capture,
                                        onCaptured = { uri ->
                                            // Crop to the guide box before analyzing, matching the
                                            // gallery path -- the model is trained on lesion-filling
                                            // crops, not whole frames.
                                            scope.launch {
                                                val croppedUri = withContext(Dispatchers.IO) {
                                                    cropCameraImageToFrame(
                                                        context, uri,
                                                        containerSizePx.width.toFloat(),
                                                        containerSizePx.height.toFloat(),
                                                        guideBoxSizePx
                                                    )
                                                }
                                                isScanning = false
                                                navController.navigate(
                                                    Screen.ScanResult.createRoute((croppedUri ?: uri).toString())
                                                )
                                            }
                                        },
                                        onFailed = { isScanning = false }
                                    )
                                }
                            }
                        },
                        enabled = !isScanning
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Scan", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }

                // Gallery
                IconButton(
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.size(52.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Tap to scan your skin", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                DiagnosticAidDisclaimer()
            }
        }
    }
}

/** Radar-style scanning sweep drawn inside the guide frame while a scan is analyzing -- a
 *  horizontal line sweeps top-to-bottom on a loop, trailing a soft gradient glow behind it,
 *  giving a genuine "scanning in progress" feel without depending on a third-party asset. */
@Composable
fun ScanningSweepEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanSweep")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepProgress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val lineY = size.height * sweepProgress
        val trailHeight = size.height * 0.4f
        val trailTop = (lineY - trailHeight).coerceAtLeast(0f)

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, DermaGreen.copy(alpha = 0.4f)),
                startY = trailTop,
                endY = lineY
            ),
            topLeft = Offset(0f, trailTop),
            size = Size(size.width, lineY - trailTop)
        )
        drawLine(
            color = DermaGreen,
            start = Offset(0f, lineY),
            end = Offset(size.width, lineY),
            strokeWidth = 3.dp.toPx()
        )
    }
}

@Composable
fun CameraPermissionDeniedScreen(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    permanentlyDenied: Boolean,
    navController: NavController
) {
    Scaffold(bottomBar = { DermaBottomNavBar(navController) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp).background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(DermaGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(44.dp), tint = DermaGreen)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Camera Access Required", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                if (permanentlyDenied)
                    "Camera access was denied and can no longer be requested from within the app. Please enable it from Settings to continue."
                else
                    "DermaLens needs camera access to scan your skin for conditions. Please grant camera permission to continue.",
                fontSize = 14.sp, color = Color(0xFF6B7280), textAlign = TextAlign.Center, lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DermaGreen)
            ) {
                Text(if (permanentlyDenied) "Open Settings" else "Grant Camera Permission", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Color(0xFFE5E7EB))
            ) {
                Text("Go Back", color = Color(0xFF374151))
            }
        }
    }
}