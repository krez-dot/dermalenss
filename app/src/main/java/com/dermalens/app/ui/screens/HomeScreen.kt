package com.dermalens.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dermalens.app.R
import com.dermalens.app.data.db.DermaDatabase
import com.dermalens.app.data.model.ScanRecord
import com.dermalens.app.navigation.Screen
import com.dermalens.app.ui.LocalAppSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Home : BottomNavItem(Screen.Home.route, Icons.Default.Home, "Home")
    object Scan : BottomNavItem(Screen.Scan.route, Icons.Default.CameraAlt, "Scan")
    object Progress : BottomNavItem(Screen.ProgressTracker.route, Icons.Default.Timeline, "Progress")
    object Profile : BottomNavItem(Screen.Profile.route, Icons.Default.Person, "Profile")
}

val bottomNavItems = listOf(BottomNavItem.Home, BottomNavItem.Scan, BottomNavItem.Progress, BottomNavItem.Profile)

@Composable
fun DermaBottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val settings = LocalAppSettings.current

    @Composable
    fun RowScope.NavItems() {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            val tint = if (selected) DermaGreen else if (settings.highContrast) Color(0xFF444444) else Color(0xFF9CA3AF)
            val navInteractionSource = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .pressScale(navInteractionSource)
                    .clickable(
                        interactionSource = navInteractionSource,
                        indication = null,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    .padding(vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected && !settings.highContrast) DermaGreenLight else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = tint)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(item.label, color = tint, fontSize = settings.textSm.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }

    // High contrast keeps the plain opaque bar -- translucency is inherently low-contrast and
    // would fight the whole point of that accessibility setting.
    if (settings.highContrast) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) { NavItems() }
        return
    }

    // Experimental "liquid glass" treatment: a floating, frosted pill instead of a flush,
    // opaque bar. No real backdrop blur (minSdk 26 predates Compose's RenderEffect blur, which
    // needs API 31+) -- translucency alone fakes the glass read safely on every supported device
    // instead of depending on a blur API that's unavailable to many. No shadow/border either --
    // both render as a hard flat outline rather than a soft blur on this emulator's renderer.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.78f)),
            verticalAlignment = Alignment.CenterVertically
        ) { NavItems() }
    }
}

val scanningTips = listOf(
    "💡 Use natural lighting when scanning your skin for best results.",
    "📏 Hold your phone 15–20 cm away from the affected area.",
    "🧴 Always consult a dermatologist for proper diagnosis.",
    "🔍 Clean the camera lens before scanning for clearer images.",
    "☀️ Avoid scanning in direct sunlight — find a well-lit indoor area.",
    "📸 Keep your hand steady while capturing — blurry images reduce accuracy.",
    "🧼 Wash and dry the skin area before scanning for best detection.",
    "🔄 Scan the same area multiple times to get consistent results."
)

@Composable
fun HomeScreen(navController: NavController) {
    val tipIndex = remember { (scanningTips.indices).random() }
    val tip = scanningTips[tipIndex]
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val db = remember { DermaDatabase.getDatabase(context) }
    val prefs = remember { context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    var userId by remember { mutableStateOf<Int?>(null) }
    var firstName by remember { mutableStateOf("") }
    var recentScan by remember { mutableStateOf<ScanRecord?>(null) }
    var scanCount by remember { mutableStateOf(0) }
    var showContributePrompt by remember {
        mutableStateOf(NewUserSignal.pendingContributePrompt.also { NewUserSignal.pendingContributePrompt = false })
    }

    LaunchedEffect(Unit) {
        val savedEmail = prefs.getString(DermaPrefs.KEY_USER_EMAIL, "") ?: ""
        val user = db.userDao().getUserByEmail(savedEmail)
        userId = user?.userId
        firstName = user?.fullName?.trim()?.split(" ")?.firstOrNull() ?: ""
    }

    LaunchedEffect(userId) {
        val id = userId ?: return@LaunchedEffect
        db.scanRecordDao().getScansByUser(id).collect { scans ->
            recentScan = scans.firstOrNull()
            scanCount = scans.size
        }
    }

    Scaffold(bottomBar = { DermaBottomNavBar(navController) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .background(if (settings.highContrast) Color.White else Color(0xFFF8F9FA))
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(colors = listOf(DermaGreen, DermaGreenDark)))
                    .padding(horizontal = 20.dp, vertical = 28.dp)
            ) {
                Column {
                    Text(if (firstName.isNotEmpty()) "Welcome back, $firstName! 👋" else "Welcome back! 👋", fontSize = settings.textMd.sp, color = Color.White.copy(alpha = 0.85f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("DermaLens", fontSize = settings.textDisplay.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Your personal skin health companion", fontSize = settings.textBase.sp, color = Color.White.copy(alpha = 0.75f))
                    if (scanCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "$scanCount scan${if (scanCount == 1) "" else "s"} completed",
                                fontSize = settings.textSm.sp,
                                color = Color.White
                            )
                        }
                    }
                }
                Image(
                    painter = painterResource(id = R.drawable.dermalens_logo),
                    contentDescription = "DermaLens logo",
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).align(Alignment.CenterEnd)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recent Scan
            EntranceAnimation { Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Recent Scan", fontSize = settings.textLg.sp, fontWeight = FontWeight.Bold, color = settings.textPrimary)
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            if (recentScan != null) navController.navigate(Screen.ProgressTracker.route)
                            else navController.navigate(Screen.Scan.route)
                        }
                        .then(if (settings.highContrast) Modifier.border(1.5.dp, Color.Black, RoundedCornerShape(16.dp)) else Modifier),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFF0F0F0) else Color.White),
                    elevation = CardDefaults.cardElevation(if (settings.highContrast) 0.dp else 2.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(DermaGreenLight), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = "Scan icon", tint = DermaGreen, modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (recentScan != null) {
                                Text(recentScan!!.condition, fontSize = settings.textMd.sp, fontWeight = FontWeight.SemiBold, color = settings.textPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("${String.format("%.1f", recentScan!!.confidence)}% confidence", fontSize = settings.textSm.sp, color = settings.textSecondary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(recentScan!!.scanDate)),
                                    fontSize = settings.textSm.sp,
                                    color = settings.textSecondary
                                )
                            } else {
                                Text("No scans yet", fontSize = settings.textMd.sp, fontWeight = FontWeight.SemiBold, color = settings.textPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Start your first skin scan today!", fontSize = settings.textBase.sp, color = settings.textSecondary)
                            }
                        }
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(DermaGreenLight), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Go to scan", tint = DermaGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Actions
            EntranceAnimation(delayMillis = 80) { Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Quick Actions", fontSize = settings.textLg.sp, fontWeight = FontWeight.Bold, color = settings.textPrimary)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionCard(icon = Icons.Default.CameraAlt, label = "Scan Skin", color = DermaGreen, modifier = Modifier.weight(1f), onClick = { navController.navigate(Screen.Scan.route) })
                    QuickActionCard(icon = Icons.Default.LocationOn, label = "Find Clinics", color = Color(0xFF0284C7), modifier = Modifier.weight(1f), onClick = { navController.navigate(Screen.ClinicLocator.route) })
                    QuickActionCard(icon = Icons.Default.Timeline, label = "Progress", color = Color(0xFF7C3AED), modifier = Modifier.weight(1f), onClick = { navController.navigate(Screen.ProgressTracker.route) })
                }
            } }

            Spacer(modifier = Modifier.height(20.dp))

            // Disclaimer
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                DiagnosticAidDisclaimer()
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tip of the Day
            EntranceAnimation(delayMillis = 160) { Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Tip of the Day", fontSize = settings.textLg.sp, fontWeight = FontWeight.Bold, color = settings.textPrimary)
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .then(if (settings.highContrast) Modifier.border(1.5.dp, DermaGreenDark, RoundedCornerShape(16.dp)) else Modifier),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFCCFBF1) else DermaGreenLight),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(DermaGreen), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Lightbulb, contentDescription = "Tip", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(tip, fontSize = settings.textBase.sp, color = if (settings.highContrast) Color(0xFF004D40) else DermaGreenDark, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                    }
                }
            } }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Contribute to Research prompt -- shown once, right after a new account finishes email
    // verification (see NewUserSignal). Existing users logging back in never see this again.
    if (showContributePrompt) {
        AlertDialog(
            onDismissRequest = { showContributePrompt = false },
            icon = { Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFF7C3AED)) },
            title = { Text("Help Improve DermaLens?", fontWeight = FontWeight.Bold, fontSize = settings.textXl.sp, color = Color(0xFF111827)) },
            text = {
                Text(
                    "Would you like to contribute your skin scan images and results to the DermaLens research dataset? This helps train and improve future versions of the AI model, especially for detecting conditions across a wider range of Filipino skin tones.\n\nYour scans are contributed anonymously and are never linked to your name or account. You can change this anytime in Profile > Contribute to Research.",
                    fontSize = settings.textMd.sp,
                    color = Color(0xFF374151),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.edit().putBoolean(DermaPrefs.KEY_CONTRIBUTE_DATA, true).apply()
                        com.dermalens.app.worker.ContributionUploadScheduler.scheduleUpload(context)
                        showContributePrompt = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Agree", fontSize = settings.textMd.sp) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        prefs.edit().putBoolean(DermaPrefs.KEY_CONTRIBUTE_DATA, false).apply()
                        showContributePrompt = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Disagree", fontSize = settings.textMd.sp) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            titleContentColor = Color(0xFF111827),
            textContentColor = Color(0xFF374151)
        )
    }
}

@Composable
fun QuickActionCard(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val settings = LocalAppSettings.current
    Card(
        modifier = modifier.clickable { onClick() }
            .then(if (settings.highContrast) Modifier.border(1.5.dp, color, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFF0F0F0) else Color.White),
        elevation = CardDefaults.cardElevation(if (settings.highContrast) 0.dp else 2.dp)
    ) {
        // Horizontal padding is deliberately tighter than vertical -- at 3-per-row on narrow
        // screens there isn't much width to spare, and 16.dp on both sides was enough to force
        // "Progress" to break mid-word ("Progres"/"s") instead of just wrapping at a space.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = if (settings.highContrast) 0.2f else 0.1f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            // minLines = 2 reserves the same height for every card's label regardless of whether
            // it actually wraps -- without this, "Find Clinics" wraps to 2 lines on narrow
            // screens (or larger accessibility font sizes) while "Scan Skin"/"Progress" stay on
            // 1, making that one card taller and breaking the row's alignment.
            Text(text = label, fontSize = settings.textBase.sp, fontWeight = FontWeight.SemiBold, color = settings.textPrimary, textAlign = TextAlign.Center, minLines = 2)
        }
    }
}