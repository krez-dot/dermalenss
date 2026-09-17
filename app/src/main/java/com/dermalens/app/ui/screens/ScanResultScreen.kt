package com.dermalens.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dermalens.app.navigation.Screen
import com.dermalens.app.ui.LocalAppSettings
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.dermalens.app.data.db.DermaDatabase
import com.dermalens.app.data.model.ScanRecord
import com.dermalens.app.data.model.User
import com.dermalens.app.ui.screens.DermaPrefs
import com.dermalens.app.ml.analysisFailedResult
import com.dermalens.app.ml.runYoloInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A detection box normalized to [0,1] relative to the analyzed image, left/top/right/bottom. */
data class NormalizedBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** One alternate condition the model considered plausible, ranked below the primary result. */
data class DifferentialCandidate(
    val condition: String,
    val confidencePercent: Float,
    val distinguishingFeature: String,
    val color: Color
)

data class DetectionResult(
    val condition: String,
    val confidence: Float,
    val severity: String,
    val description: String,
    val symptoms: List<String>,
    val recommendation: String,
    val color: Color,
    val boundingBoxes: List<NormalizedBox> = emptyList(),
    // What visually sets this condition apart from ones it commonly gets confused with. Shown as
    // this condition's line when it appears as a differential candidate for a different result.
    val distinguishingFeature: String = "",
    // Other conditions the model's own class scores ranked as plausible for this scan, most
    // confident first. Empty when the model has only one trained class (nothing to rank against)
    // or when no other class scored above the noise floor.
    val differentials: List<DifferentialCandidate> = emptyList(),
    // True when this isn't a real diagnosis, just a below-the-confidence-floor fallback
    // (see YoloDetector.kt). The UI hides the confidence percentage in this case -- showing
    // a number next to "no result" undermines the point of not committing to an answer.
    val isLowConfidence: Boolean = false
)

val mockDetectionResults = listOf(
    // The currently-bundled 6-class model still outputs "Acne Vulgaris" as one label (the acne
    // subtype split below is a standalone experiment, not yet folded into the bundled model -- see
    // training/acne_subtypes_future and train_acne_subtypes.ipynb). Keep this entry until the
    // bundled model itself is retrained on the 5 subtypes, or conditionTemplates[label] returns
    // null for a real Acne Vulgaris detection and the result screen hangs on "Analyzing your scan..."
    DetectionResult("Acne Vulgaris", 94.3f, "Moderate", "Acne vulgaris is a common skin condition that occurs when hair follicles become clogged with oil and dead skin cells, causing whiteheads, blackheads, or pimples.", listOf("Whiteheads", "Blackheads", "Papules", "Pustules"), "Consult a dermatologist. Use gentle cleansers and avoid picking or squeezing affected areas.", Color(0xFFE53935), distinguishingFeature = "Clusters of whiteheads, blackheads, or inflamed pimples concentrated on the face, chest, or back."),
    DetectionResult("Blackhead Acne", 94.3f, "Mild", "Blackheads are a mild, non-inflammatory form of acne that occurs when a hair follicle becomes clogged with oil and dead skin cells, and the clog's surface oxidizes and turns dark when exposed to air.", listOf("Small dark or black bumps", "Flat or slightly raised", "No redness or pain", "Common on nose, forehead, and chin"), "Use salicylic acid or benzoyl peroxide cleansers to help unclog pores. Avoid squeezing, which can cause scarring. Consult a dermatologist for persistent cases.", Color(0xFF424242), distinguishingFeature = "Small dark, flat bumps with no surrounding redness or inflammation — the clogged pore's surface has oxidized to a dark color."),
    DetectionResult("Whitehead Acne", 92.1f, "Mild", "Whiteheads are a mild, non-inflammatory form of acne where a clogged hair follicle stays closed beneath the skin's surface, forming a small white or flesh-colored bump.", listOf("Small white or skin-colored bumps", "Closed, not open like blackheads", "No redness or pain", "Often found in clusters"), "Use gentle exfoliants or retinoid creams to help clear clogged pores. Avoid picking, which can lead to inflammation or scarring. Consult a dermatologist if it persists.", Color(0xFFFFB300), distinguishingFeature = "Small closed bumps just under the skin's surface, white or skin-toned, without the dark oxidized center seen in blackheads."),
    DetectionResult("Papular Acne", 90.4f, "Moderate", "Papules are small, inflamed acne bumps that form when a clogged pore's walls break down, causing redness and mild swelling without visible pus.", listOf("Small red, raised bumps", "Tender to touch", "No visible pus", "Can be widespread or localized"), "Use benzoyl peroxide or topical retinoids to reduce inflammation. Avoid picking to prevent scarring. Consult a dermatologist if papules are widespread or persistent.", Color(0xFFE53935), distinguishingFeature = "Small, firm, red bumps without a visible white or yellow center — inflamed but not yet pus-filled."),
    DetectionResult("Pustular Acne", 91.8f, "Moderate", "Pustules are inflamed acne lesions filled with pus, appearing as red bumps with a white or yellow center. They form when the body's immune response to clogged, infected pores intensifies.", listOf("Red bumps with white or yellow center", "Tender or painful", "Pus-filled", "Common on face, chest, or back"), "Use benzoyl peroxide or prescribed topical/oral antibiotics. Avoid squeezing, which can spread infection and cause scarring. Consult a dermatologist for persistent or widespread pustules.", Color(0xFFFF7043), distinguishingFeature = "Red, inflamed bumps with a distinct white or yellow pus-filled center, unlike the solid red bumps of papules."),
    DetectionResult("Nodular Acne", 89.7f, "Severe", "Nodules are large, firm, and often painful lumps that form deep under the skin when clogged, inflamed pores damage surrounding tissue. This is a more severe form of acne that can lead to scarring.", listOf("Large, firm lumps under the skin", "Painful to touch", "Deep-seated, not surface-level", "Can persist for weeks"), "Seek dermatologist care — nodular acne often requires prescription oral medication and is prone to scarring if untreated. Avoid picking or squeezing.", Color(0xFFB71C1C), distinguishingFeature = "Large, firm, painful lumps deep under the skin, unlike the smaller surface-level bumps of other acne types."),
    DetectionResult("Eczema", 87.6f, "Mild", "Eczema (atopic dermatitis) is a condition that makes your skin red and itchy. It is common in children but can occur at any age.", listOf("Dry skin", "Itching", "Red patches", "Skin flaking"), "Keep skin moisturized. Avoid known triggers. Consult a dermatologist for topical treatments.", Color(0xFFFF9800), distinguishingFeature = "Diffuse dry, itchy, red patches with no sharp border, often in skin folds like elbows and knees."),
    DetectionResult("Melasma", 91.2f, "Mild", "Melasma is a skin condition presenting as brown or blue-gray patches, usually on the face. It is associated with hormonal changes and sun exposure.", listOf("Brown patches", "Facial discoloration", "Symmetrical patches"), "Use broad-spectrum sunscreen daily. Avoid sun exposure. Consult a dermatologist for treatment options.", Color(0xFF795548), distinguishingFeature = "Flat, symmetrical brown patches on sun-exposed areas like the cheeks and forehead — no itching or raised texture."),
    DetectionResult("Tinea", 89.5f, "Moderate", "Tinea is a fungal infection of the skin. It can affect different parts of the body and is usually characterized by a ring-shaped rash.", listOf("Ring-shaped rash", "Itching", "Scaly skin", "Redness"), "Use antifungal cream as prescribed. Keep skin dry and clean. Consult a dermatologist.", Color(0xFF4CAF50), distinguishingFeature = "A distinct ring-shaped patch with a raised, scaly border and a clearer center, spreading outward."),
    DetectionResult("Warts", 96.1f, "Mild", "Warts are small growths caused by the human papillomavirus (HPV). They can appear anywhere on the body and are usually harmless.", listOf("Small flesh-colored bumps", "Rough texture", "Black dots"), "Avoid touching or scratching warts. Consult a dermatologist for removal options.", Color(0xFF9C27B0), distinguishingFeature = "Small, rough-textured, flesh-colored bumps, sometimes with tiny black dots — usually not itchy or red."),
    DetectionResult("Scabies", 88.4f, "Severe", "Scabies is an itchy skin condition caused by a tiny burrowing mite. The intense itching associated with scabies is an allergic reaction to the mite.", listOf("Intense itching", "Thin burrow tracks", "Rash", "Sores"), "Seek immediate medical attention. Treatment requires prescription medication. Wash all clothing and bedding.", Color(0xFFF44336), distinguishingFeature = "Intense itching that's worse at night, with thin thread-like burrow tracks, often between fingers or on wrists.")
)

/**
 * Inserts or updates this scan's history row. Shared by both the "Save to History" and
 * "Contribute to Research" buttons so a user who taps both ends up with one row, not two --
 * passing back the previous call's returned id makes the second insert an update (Room's
 * REPLACE conflict strategy) rather than a duplicate. [contribute] controls only whether the
 * image gets copied out and the row flagged for upload; it never happens as a side effect of
 * plain saving.
 */
private suspend fun saveScan(
    context: android.content.Context,
    result: DetectionResult,
    imageUri: String?,
    existingScanId: Int?,
    contribute: Boolean
): Int? {
    val db = DermaDatabase.getDatabase(context)
    val prefs = context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val savedEmail = prefs.getString(DermaPrefs.KEY_USER_EMAIL, "") ?: ""
    var user = db.userDao().getUserByEmail(savedEmail)
    if (user == null && savedEmail.isNotBlank()) {
        // No local profile row for this logged-in session (e.g. it was lost to a schema
        // migration) -- self-heal the same way Login's sign-in flow does, so saving a scan
        // doesn't silently no-op.
        db.userDao().insertUser(User(fullName = savedEmail.substringBefore("@"), email = savedEmail, passwordHash = ""))
        user = db.userDao().getUserByEmail(savedEmail)
    }
    if (user == null) return existingScanId

    var savedImagePath = ""
    if (contribute && imageUri != null) {
        savedImagePath = withContext(Dispatchers.IO) {
            try {
                val dir = java.io.File(context.filesDir, "contributed_scans")
                dir.mkdirs()
                val file = java.io.File(dir, "${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(android.net.Uri.parse(imageUri))?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file.absolutePath
            } catch (e: Exception) { "" }
        }
    }

    val id = db.scanRecordDao().insertScan(
        ScanRecord(
            id = existingScanId ?: 0,
            userId = user.userId,
            condition = result.condition,
            confidence = result.confidence,
            severity = result.severity,
            notes = "Scanned on ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}",
            imagePath = savedImagePath,
            contributedForTraining = contribute && savedImagePath.isNotEmpty()
        )
    )
    return id.toInt()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(navController: NavController, imageUri: String? = null) {
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val loadedResult by produceState<DetectionResult?>(initialValue = null, imageUri) {
        value = withContext(Dispatchers.Default) {
            // Never fall back to mockDetectionResults.random() here: that turned any inference
            // failure into a randomly invented diagnosis, complete with a confidence number.
            imageUri?.let { runYoloInference(context, it) } ?: analysisFailedResult()
        }
    }

    if (loadedResult == null) {
        Scaffold { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(if (settings.highContrast) Color.White else Color(0xFFF8F9FA)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = DermaGreen)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Analyzing your scan...", fontSize = settings.textMd.sp, color = Color(0xFF6B7280))
                }
            }
        }
        return
    }
    val result = loadedResult!!
    var isSaved by remember { mutableStateOf(false) }
    var isContributed by remember { mutableStateOf(false) }
    // Reused across Save to History and Contribute to Research so a user who taps both ends up
    // with one updated row, not two -- Room's REPLACE conflict strategy overwrites by id.
    var savedScanId by remember { mutableStateOf<Int?>(null) }
    // Read once, at composable entry, purely to decide whether the Contribute button shows at
    // all -- someone who opted out entirely in Profile shouldn't see a per-scan prompt for a
    // feature they've already declined.
    val contributionFeatureEnabled = remember {
        context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getBoolean(DermaPrefs.KEY_CONTRIBUTE_DATA, false)
    }
    // Shown once, the first time a low-confidence result loads -- the inline "No Clear Condition
    // Detected" card below still renders underneath, so dismissing the dialog (rather than
    // retaking) still leaves the user somewhere useful instead of a dead end.
    var showLowConfidenceDialog by remember { mutableStateOf(result.isLowConfidence) }
    // Set true only when Save to History actually also triggered a research upload (contribute
    // toggle on + image copy succeeded) -- not on every save, since most saves don't contribute.
    var showContributionDialog by remember { mutableStateOf(false) }
    // The actual consent moment -- shown right after a successful save, only if the Contribute
    // to Research feature is enabled in Profile. A real yes/no choice, not a one-button prompt:
    // this feature is anonymous and opt-in per the app's own Privacy Policy text, so the user
    // has to be able to genuinely say no.
    var showContributePrompt by remember { mutableStateOf(false) }
    // Matches the image's real aspect ratio once loaded so the overlay box (normalized 0..1 to
    // the image itself) lines up pixel-for-pixel with no letterbox offset to account for.
    var imageAspectRatio by remember(imageUri) { mutableStateOf(1f) }

    // The result "reveal" -- this composable only reaches this point once, the first time
    // loadedResult resolves (the isLoading branch above returns early), so a plain remember +
    // LaunchedEffect toggle is enough to trigger a genuine one-time entrance, not a replay on
    // every recomposition.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    if (showContributePrompt) {
        AlertDialog(
            onDismissRequest = { showContributePrompt = false },
            icon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = DermaGreen) },
            title = { Text("Contribute to Research?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Would you like to also contribute this scan to help improve future versions " +
                        "of the detection model? The image is uploaded anonymously -- no name, " +
                        "email, or account info is attached, only the image and its detected " +
                        "condition.",
                    fontSize = settings.textMd.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showContributePrompt = false
                    scope.launch {
                        savedScanId = saveScan(context, result, imageUri, savedScanId, contribute = true)
                        isContributed = true
                        com.dermalens.app.worker.ContributionUploadScheduler.triggerImmediateUpload(context)
                        showContributionDialog = true
                    }
                }) { Text("Yes, contribute") }
            },
            dismissButton = {
                TextButton(onClick = { showContributePrompt = false }) { Text("No thanks") }
            }
        )
    }

    if (showContributionDialog) {
        AlertDialog(
            onDismissRequest = { showContributionDialog = false },
            icon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = DermaGreen) },
            title = { Text("Thank You!", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This image has been uploaded to our secure cloud storage. Thank you for " +
                        "contributing to research! 😊",
                    fontSize = settings.textMd.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showContributionDialog = false }) { Text("OK") }
            }
        )
    }

    if (showLowConfidenceDialog) {
        AlertDialog(
            onDismissRequest = { showLowConfidenceDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF6B7280)) },
            title = { Text("Low Confidence", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The scan didn't clearly match any condition this app recognizes. This can happen if the " +
                        "photo isn't of skin, is blurry or poorly lit, or doesn't clearly show an affected area. " +
                        "For the most reliable result, retake the photo in good lighting with the affected area " +
                        "filling the frame.",
                    fontSize = settings.textMd.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLowConfidenceDialog = false
                    navController.navigate(Screen.Scan.route) { popUpTo(Screen.Scan.route) { inclusive = true } }
                }) { Text("Retake Photo") }
            },
            dismissButton = {
                TextButton(onClick = { showLowConfidenceDialog = false }) { Text("View Details") }
            }
        )
    }

    Scaffold(
        topBar = {
            DermaGlassTopBar(
                title = "Scan Result",
                onBack = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = false } } },
                titleColor = Color(0xFF111827)
            )
        }
    ) { innerPadding ->
        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 0.94f)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (settings.highContrast) Color.White else Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Detection Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(colors = listOf(result.color.copy(alpha = 0.9f), result.color)))
                    .padding(24.dp)
                    .semantics { contentDescription = if (result.isLowConfidence) "Detected condition: ${result.condition}" else "Detected condition: ${result.condition}, ${result.confidence}% confidence" }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(imageAspectRatio)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUri != null) {
                            AsyncImage(
                                model = android.net.Uri.parse(imageUri),
                                contentDescription = "Scanned image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                onSuccess = { state ->
                                    val w = state.result.drawable.intrinsicWidth
                                    val h = state.result.drawable.intrinsicHeight
                                    if (w > 0 && h > 0) imageAspectRatio = w.toFloat() / h.toFloat()
                                }
                            )
                            if (result.boundingBoxes.isNotEmpty()) {
                                Canvas(modifier = Modifier.fillMaxSize().semantics { contentDescription = "${result.boundingBoxes.size} detected area(s) highlighted on scanned image" }) {
                                    result.boundingBoxes.forEach { box ->
                                        val left = box.left * size.width
                                        val top = box.top * size.height
                                        val right = box.right * size.width
                                        val bottom = box.bottom * size.height
                                        drawRect(
                                            color = Color.White,
                                            topLeft = Offset(left, top),
                                            size = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
                                            style = Stroke(width = 3.dp.toPx())
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ImageSearch, contentDescription = "Scan image placeholder", tint = Color.White, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Scan Image", fontSize = settings.textSm.sp, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(result.condition, fontSize = settings.textTitle.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!result.isLowConfidence) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp).semantics { contentDescription = "${result.confidence}% confidence" },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("%.1f%%".format(result.confidence), color = Color.White, fontSize = settings.textBase.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // About Card
                ResultCard(icon = Icons.Default.Info, iconBg = DermaGreenLight, iconTint = DermaGreen, title = "About this condition") {
                    Text(result.description, fontSize = settings.textMd.sp, color = Color(0xFF374151), lineHeight = 22.sp)
                }

                if (!result.isLowConfidence && familyTrees.containsKey(result.condition)) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Family Tree Card -- static reference only, see FamilyTree.kt. Hidden on a
                    // low-confidence result since there's no confirmed condition to show a tree for.
                    ResultCard(icon = Icons.Default.AccountTree, iconBg = Color(0xFFF3E8FF), iconTint = DermaGreen, title = "Related Conditions") {
                        Text(
                            "See how ${result.condition} relates to similar-looking conditions and its own subtypes.",
                            fontSize = settings.textSm.sp,
                            color = Color(0xFF6B7280),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { navController.navigate(Screen.FamilyTree.createRoute(result.condition)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Family Tree")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Symptoms Card -- for a low-confidence result, `result.symptoms` actually holds
                // retake tips (see YoloDetector.kt's lowConfidenceResult), not real symptoms, so
                // the heading needs to match what's actually listed underneath it.
                ResultCard(
                    icon = Icons.Default.List,
                    iconBg = Color(0xFFFEF3C7),
                    iconTint = Color(0xFFD97706),
                    title = if (result.isLowConfidence) "What You Can Try" else "Common Symptoms"
                ) {
                    result.symptoms.forEach { symptom ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp).semantics { contentDescription = "Symptom: $symptom" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(result.color))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(symptom, fontSize = settings.textMd.sp, color = Color(0xFF374151))
                        }
                    }
                }

                if (result.differentials.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Differential Diagnosis Card
                    ResultCard(icon = Icons.Default.Rule, iconBg = Color(0xFFEFF6FF), iconTint = Color(0xFF2563EB), title = "Other Possibilities") {
                        Text(
                            "The scan also picked up some resemblance to these conditions. A dermatologist should confirm which one it actually is.",
                            fontSize = settings.textSm.sp,
                            color = Color(0xFF6B7280),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        result.differentials.forEach { candidate ->
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp)
                                    .semantics { contentDescription = "Also considered: ${candidate.condition}, ${candidate.confidencePercent}% confidence" },
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(modifier = Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(candidate.color))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(candidate.condition, fontSize = settings.textMd.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1a1a1a))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("%.1f%%".format(candidate.confidencePercent), fontSize = settings.textSm.sp, color = Color(0xFF6B7280))
                                    }
                                    Text(candidate.distinguishingFeature, fontSize = settings.textSm.sp, color = Color(0xFF6B7280), lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Recommendation Card
                ResultCard(icon = Icons.Default.Lightbulb, iconBg = DermaGreenLight, iconTint = DermaGreen, title = "Recommendation", bgColor = DermaGreenLight) {
                    Text(result.recommendation, fontSize = settings.textMd.sp, color = DermaGreenDark, lineHeight = 22.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Disclaimer
                DiagnosticAidDisclaimer()

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Button(
                    onClick = { navController.navigate(Screen.ClinicLocator.route) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Find Nearby Clinic", fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (!isSaved) {
                            scope.launch {
                                // contribute = false here, deliberately -- saving to history must
                                // never silently also upload the image. Whether to contribute is
                                // asked right after, as its own explicit yes/no prompt, so consent
                                // is real rather than a side effect of tapping this button.
                                savedScanId = saveScan(context, result, imageUri, savedScanId, contribute = false)
                                isSaved = true
                                if (contributionFeatureEnabled && !isContributed) {
                                    showContributePrompt = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = if (isSaved) "Scan saved to history" else "Save scan to history" },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSaved) Color(0xFF16A34A) else Color(0xFF0284C7))
                ) {
                    Icon(if (isSaved) Icons.Default.Check else Icons.Default.BookmarkAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSaved) "Saved to History!" else "Save to History", fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { navController.navigate(Screen.Scan.route) { popUpTo(Screen.Scan.route) { inclusive = true } } },
                    modifier = Modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = "Scan again" },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFE5E7EB))
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF374151), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Again", fontSize = settings.textLg.sp, color = Color(0xFF374151))
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        }
    }
}

@Composable
fun ResultCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    bgColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    val settings = LocalAppSettings.current
    Card(
        modifier = Modifier.fillMaxWidth()
            .then(if (settings.highContrast) Modifier.border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFF0F0F0) else bgColor),
        elevation = CardDefaults.cardElevation(if (settings.highContrast) 0.dp else if (bgColor == Color.White) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}