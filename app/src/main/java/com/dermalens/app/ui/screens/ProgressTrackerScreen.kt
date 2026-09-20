package com.dermalens.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.dermalens.app.data.db.DermaDatabase
import com.dermalens.app.navigation.Screen
import com.dermalens.app.ui.LocalAppSettings
import kotlinx.coroutines.launch

data class ScanEntry(val id: Int, val date: String, val confidence: Float, val notes: String, val imagePath: String = "")
data class ConditionTrack(val condition: String, val color: Color, val emoji: String, val scans: List<ScanEntry>)

val mockProgressData = listOf(
    ConditionTrack("Papular Acne", Color(0xFFE53935), "🔴", listOf(
        ScanEntry(0, "May 1, 2026", 94.3f, "Initial scan — widespread breakout"),
        ScanEntry(0, "May 5, 2026", 89.2f, "Slight improvement after treatment"),
        ScanEntry(0, "May 10, 2026", 91.5f, "Significant improvement noted"),
    )),
    ConditionTrack("Eczema", Color(0xFFFF9800), "🟠", listOf(
        ScanEntry(0, "Apr 20, 2026", 87.6f, "Flare-up detected on forearm"),
        ScanEntry(0, "Apr 28, 2026", 85.1f, "Moisturizer routine helping"),
    )),
    ConditionTrack("Melasma", Color(0xFF795548), "🟤", listOf(
        ScanEntry(0, "May 3, 2026", 91.2f, "Brown patches on cheeks detected"),
    ))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressTrackerScreen(navController: NavController) {
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val db = remember { DermaDatabase.getDatabase(context) }
    val prefs = remember { context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE) }

    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableStateOf(0) }
    var totalScans by remember { mutableStateOf(0) }
    var daysTracked by remember { mutableStateOf(0) }
    var conditionTracks by remember { mutableStateOf(emptyList<ConditionTrack>()) }

    LaunchedEffect(refreshKey) {
        val savedEmail = prefs.getString(DermaPrefs.KEY_USER_EMAIL, "") ?: ""
        val user = db.userDao().getUserByEmail(savedEmail)
        if (user != null) {
            val scans = db.scanRecordDao().getScansByUserOnce(user.userId)
            totalScans = scans.size
            if (scans.isNotEmpty()) {
                val earliest = scans.minOf { it.scanDate }
                daysTracked = ((System.currentTimeMillis() - earliest) / (1000L * 60L * 60L * 24L)).toInt() + 1
            }
            val grouped = scans.groupBy { it.condition }
            conditionTracks = grouped.map { (condition, scanList) ->
                val mockTrack = mockProgressData.find { it.condition == condition }
                ConditionTrack(
                    condition = condition,
                    color = mockTrack?.color ?: Color(0xFF7C3AED),
                    emoji = mockTrack?.emoji ?: "🔵",
                    // Ascending by date (oldest first) -- the DAO query itself returns newest
                    // first (for other screens that want that), but this timeline's visuals
                    // (filled dot, highlighted card, "Latest scan" label) are built around the
                    // *last* list item being the most recent one, matching the natural top-to-
                    // bottom "journey" reading of a progress timeline.
                    scans = scanList.sortedBy { it.scanDate }.map { scan ->
                        ScanEntry(
                            id = scan.id,
                            date = java.text.SimpleDateFormat("MMM dd, yyyy • h:mm a", java.util.Locale.getDefault())
                                .format(java.util.Date(scan.scanDate)),
                            confidence = scan.confidence,
                            notes = scan.notes,
                            imagePath = scan.imagePath
                        )
                    }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            DermaGlassTopBar(
                title = "Progress Tracker",
                onBack = { navController.popBackStack() },
                titleColor = settings.textPrimary
            )
        },
        bottomBar = { DermaBottomNavBar(navController) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).background(if (settings.highContrast) Color.White else Color(0xFFF8F9FA)),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(DermaGreen, DermaGreenDark)))
                        .padding(20.dp)
                ) {
                    Column {
                        Text("Your Skin Journey", fontSize = settings.textXxl.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Track your progress over time", fontSize = settings.textBase.sp, color = Color.White.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("$totalScans", "Total Scans", Modifier.weight(1f))
                            StatCard("${conditionTracks.size}", "Conditions", Modifier.weight(1f))
                            StatCard("$daysTracked", "Days Tracked", Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    DiagnosticAidDisclaimer()
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (settings.highContrast) Color(0xFFCCFBF1) else DermaGreenLight)
                        .then(if (settings.highContrast) Modifier.border(1.dp, DermaGreenDark, RoundedCornerShape(12.dp)) else Modifier)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💡", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Scan regularly to track your skin's improvement over time!", fontSize = settings.textBase.sp, color = if (settings.highContrast) Color(0xFF004D40) else DermaGreenDark, lineHeight = 18.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text("Condition Timelines", fontSize = settings.textLg.sp, fontWeight = FontWeight.Bold, color = settings.textPrimary, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (conditionTracks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(DermaGreenLight), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = DermaGreen, modifier = Modifier.size(34.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No scans yet!",
                            fontSize = settings.textLg.sp,
                            fontWeight = FontWeight.Bold,
                            color = settings.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Start scanning to track your skin health journey.",
                            fontSize = settings.textBase.sp,
                            color = settings.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { navController.navigate(Screen.Scan.route) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DermaGreen)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Your First Scan", fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            itemsIndexed(conditionTracks) { index, track ->
                EntranceAnimation(delayMillis = index * 70) {
                    Column {
                        ConditionTrackCard(
                            track = track,
                            onScanAgain = { navController.navigate(Screen.Scan.route) },
                            onDeleteScan = { scanId ->
                                scope.launch {
                                    db.scanRecordDao().deleteScan(scanId)
                                    refreshKey++
                                }
                            },
                            onEditNote = { scanId, newNote ->
                                scope.launch {
                                    db.scanRecordDao().updateNotes(scanId, newNote)
                                    refreshKey++
                                }
                            },
                            onOpenScan = { scan ->
                                navController.navigate(Screen.ScanResult.createRoute(imageUri = scan.imagePath.ifEmpty { null }, scanId = scan.id))
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            if (conditionTracks.isNotEmpty()) {
                item {
                    Button(
                        onClick = { navController.navigate(Screen.Scan.route) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DermaGreen)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start New Scan", fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

        }
    }
}

@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.2f)).padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ConditionTrackCard(track: ConditionTrack, onScanAgain: () -> Unit, onDeleteScan: (Int) -> Unit, onEditNote: (Int, String) -> Unit, onOpenScan: (ScanEntry) -> Unit) {
    var isExpanded by remember { mutableStateOf(true) }
    val settings = LocalAppSettings.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .then(if (settings.highContrast) Modifier.border(1.dp, Color.Black, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFF0F0F0) else Color.White),
        elevation = CardDefaults.cardElevation(if (settings.highContrast) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(track.color.copy(alpha = if (settings.highContrast) 0.2f else 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(track.emoji, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.condition, fontSize = settings.textMd.sp, fontWeight = FontWeight.Bold, color = settings.textPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Last scan: ${track.scans.lastOrNull()?.date?.substringBefore(" • ") ?: "--"}",
                        fontSize = settings.textBase.sp,
                        color = settings.textSecondary,
                        maxLines = 1
                    )
                }
                Box(modifier = Modifier.background(track.color.copy(alpha = if (settings.highContrast) 0.2f else 0.1f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("${track.scans.size} ${if (track.scans.size == 1) "scan" else "scans"}", fontSize = settings.textSm.sp, color = track.color, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                val chevronRotation by animateFloatAsState(if (isExpanded) 0f else 180f, label = "chevronRotation")
                Icon(
                    Icons.Default.ExpandLess,
                    contentDescription = null,
                    tint = if (settings.highContrast) Color(0xFF444444) else Color(0xFF9CA3AF),
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronRotation }
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(280)) + fadeIn(tween(280)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(180))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = if (settings.highContrast) Color(0xFFCCCCCC) else Color(0xFFF3F4F6))
                    Spacer(modifier = Modifier.height(16.dp))

                    track.scans.forEachIndexed { index, scan ->
                        TimelineNode(
                            scan = scan,
                            isFirst = index == 0,
                            isLast = index == track.scans.size - 1,
                            color = track.color,
                            onDelete = { onDeleteScan(scan.id) },
                            onEditNote = { newNote -> onEditNote(scan.id, newNote) },
                            onOpenScan = { onOpenScan(scan) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onScanAgain,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = track.color)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Again", fontSize = settings.textBase.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineNode(scan: ScanEntry, isFirst: Boolean, isLast: Boolean, color: Color, onDelete: () -> Unit, onEditNote: (String) -> Unit, onOpenScan: () -> Unit) {
    val settings = LocalAppSettings.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditNoteDialog by remember { mutableStateOf(false) }
    var editedNote by remember(scan.id, scan.notes) { mutableStateOf(scan.notes) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color.White,
            titleContentColor = Color(0xFF111827),
            textContentColor = Color(0xFF374151),
            title = { Text("Delete Scan?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove the scan from ${scan.date}. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = Color(0xFF6B7280))
                }
            }
        )
    }

    if (showEditNoteDialog) {
        AlertDialog(
            onDismissRequest = { showEditNoteDialog = false },
            containerColor = Color.White,
            titleContentColor = Color(0xFF111827),
            textContentColor = Color(0xFF374151),
            title = { Text("Edit Note", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editedNote,
                    onValueChange = { editedNote = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    placeholder = { Text("How does it look today?") }
                )
            },
            confirmButton = {
                TextButton(onClick = { showEditNoteDialog = false; onEditNote(editedNote) }) {
                    Text("Save", color = color, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editedNote = scan.notes; showEditNoteDialog = false }) {
                    Text("Cancel", color = Color(0xFF6B7280))
                }
            }
        )
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!isFirst) Box(modifier = Modifier.width(2.dp).height(8.dp).background(color.copy(alpha = 0.25f)))
            else Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.size(14.dp).clip(CircleShape)
                    .background(if (isLast) color else color.copy(alpha = 0.4f))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isLast) Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.White))
            }
            if (!isLast) Box(modifier = Modifier.width(2.dp).height(40.dp).background(color.copy(alpha = 0.25f)))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = if (!isLast) 4.dp else 0.dp)
                .then(if (settings.highContrast && isLast) Modifier.border(1.dp, color, RoundedCornerShape(12.dp)) else Modifier)
                .clickable { onOpenScan() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    settings.highContrast && isLast -> color.copy(alpha = 0.12f)
                    settings.highContrast -> Color(0xFFEEEEEE)
                    isLast -> color.copy(alpha = 0.06f)
                    else -> Color(0xFFF9FAFB)
                }
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(scan.date, fontSize = settings.textBase.sp, color = settings.textSecondary, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("%.1f%%".format(scan.confidence), fontSize = settings.textSm.sp, color = color, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete scan", tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (scan.imagePath.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = scan.imagePath,
                        contentDescription = "Scan photo from ${scan.date}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(scan.notes, fontSize = settings.textBase.sp, color = settings.textPrimary, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { editedNote = scan.notes; showEditNoteDialog = true }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit note", tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
                    }
                }
                if (isLast) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Latest scan", fontSize = settings.textSm.sp, color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

