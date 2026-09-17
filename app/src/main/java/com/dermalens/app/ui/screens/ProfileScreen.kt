package com.dermalens.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dermalens.app.R
import com.dermalens.app.navigation.Screen
import com.dermalens.app.ui.LocalAppSettings
import androidx.compose.runtime.LaunchedEffect
import com.dermalens.app.data.db.DermaDatabase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bridges Firebase's callback-based Task API into a suspend call, so reauthenticate() and
 * updatePassword() can be awaited in sequence like any other suspend function, without pulling
 * in the kotlinx-coroutines-play-services dependency just for this. */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { e -> cont.resumeWithException(e) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showContributeDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteAccountPassword by remember { mutableStateOf("") }
    var deleteAccountError by remember { mutableStateOf("") }
    var isDeletingAccount by remember { mutableStateOf(false) }

    val db = remember { DermaDatabase.getDatabase(context) }
    var userName by remember { mutableStateOf("User") }
    var userEmail by remember { mutableStateOf("") }
    var memberSince by remember { mutableStateOf("") }
    var totalScans by remember { mutableStateOf(0) }
    var conditions by remember { mutableStateOf(0) }
    var daysActive by remember { mutableStateOf(0) }

    val prefs = remember { context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    var fontScale by remember { mutableStateOf(prefs.getFloat(DermaPrefs.KEY_FONT_SIZE, 1.0f)) }
    var highContrast by remember { mutableStateOf(prefs.getBoolean(DermaPrefs.KEY_HIGH_CONTRAST, false)) }
    var contributeData by remember { mutableStateOf(prefs.getBoolean(DermaPrefs.KEY_CONTRIBUTE_DATA, false)) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean(DermaPrefs.KEY_NOTIFICATIONS_ENABLED, true)) }

    LaunchedEffect(Unit) {
        val savedEmail = prefs.getString(DermaPrefs.KEY_USER_EMAIL, "") ?: ""
        val user = db.userDao().getUserByEmail(savedEmail)
        if (user != null) {
            userName = user.fullName
            userEmail = user.email
            memberSince = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(user.createdAt))
            totalScans = db.scanRecordDao().getScanCount(user.userId)
            conditions = db.scanRecordDao().getConditionsByUser(user.userId).size
            daysActive = ((System.currentTimeMillis() - user.createdAt) / (1000L * 60L * 60L * 24L)).toInt() + 1
        }
    }

    Scaffold(
        topBar = {
            DermaGlassTopBar(title = "My Profile", titleColor = settings.textPrimary)
        },
        bottomBar = { DermaBottomNavBar(navController) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .background(if (settings.highContrast) Color.White else Color(0xFFF8F9FA))
        ) {
            // Header
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(colors = listOf(DermaGreen, DermaGreenDark)))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(88.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(if (settings.highContrast) 3.dp else 2.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(userName.split(" ").filter { it.isNotEmpty() }.take(2).map { it.first() }.joinToString("").ifEmpty { "?" }, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(userName, fontSize = settings.textXl.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(userEmail, fontSize = settings.textBase.sp, color = Color.White.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 5.dp)) {
                        Text("Member since $memberSince", fontSize = settings.textBase.sp, color = Color.White)
                    }
                }
            }


            Spacer(modifier = Modifier.height(20.dp))

            // Stats
            EntranceAnimation { Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-1).dp)
                    .then(if (settings.highContrast) Modifier.border(1.dp, Color.Black, RoundedCornerShape(16.dp)) else Modifier),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFF0F0F0) else Color.White),
                elevation = CardDefaults.cardElevation(if (settings.highContrast) 0.dp else 4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ProfileStatItem("$totalScans", "Total Scans", "📷")
                    VerticalDivider(modifier = Modifier.height(40.dp), color = if (settings.highContrast) Color(0xFFCCCCCC) else Color(0xFFF3F4F6))
                    ProfileStatItem("$conditions", "Conditions", "🔍")
                    VerticalDivider(modifier = Modifier.height(40.dp), color = if (settings.highContrast) Color(0xFFCCCCCC) else Color(0xFFF3F4F6))
                    ProfileStatItem("$daysActive", "Days Active", "📅")
                }
            } }

            Spacer(modifier = Modifier.height(16.dp))

            // Disclaimer — placed near the top so it's one of the first things seen
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                DiagnosticAidDisclaimer()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Account Section
            EntranceAnimation(delayMillis = 60) { Column {
            ProfileSectionHeader("Account")
            Spacer(modifier = Modifier.height(8.dp))
            ProfileMenuCard {
                ProfileMenuItem(icon = Icons.Default.Edit, iconBg = DermaGreenLight, iconTint = DermaGreen, title = "Edit Profile", subtitle = "Update your name and email", onClick = { navController.navigate(Screen.EditProfile.route) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = if (settings.highContrast) Color(0xFFCCCCCC) else Color(0xFFF3F4F6))
                ProfileMenuItemSwitch(icon = Icons.Default.Notifications, iconBg = Color(0xFFEFF6FF), iconTint = Color(0xFF2563EB), title = "Scan Reminders", subtitle = if (notificationsEnabled) "Reminders are ON" else "Reminders are OFF", checked = notificationsEnabled, onCheckedChange = {
                    notificationsEnabled = it
                    prefs.edit().putBoolean(DermaPrefs.KEY_NOTIFICATIONS_ENABLED, it).apply()
                    if (it) {
                        com.dermalens.app.worker.NotificationScheduler.scheduleDailyReminder(context)
                    } else {
                        com.dermalens.app.worker.NotificationScheduler.cancelReminder(context)
                    }
                })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = if (settings.highContrast) Color(0xFFCCCCCC) else Color(0xFFF3F4F6))
                // Fires a real notification ~5s later via a one-time WorkRequest, not a fake
                // in-app toast pretending to be one -- see NotificationScheduler.scheduleTestReminder
                // and feedback_workmanager_testing memory: force-running a periodic job directly is
                // unreliable, a genuine OneTimeWorkRequest is the reliable way to demo this on demand.
                ProfileMenuItem(icon = Icons.Default.NotificationsActive, iconBg = Color(0xFFFEF3C7), iconTint = Color(0xFFD97706), title = "Test Notification", subtitle = "Send a sample reminder in ~5 seconds", onClick = {
                    com.dermalens.app.worker.NotificationScheduler.scheduleTestReminder(context)
                    android.widget.Toast.makeText(context, "Test notification queued -- check your notification shade in ~5s", android.widget.Toast.LENGTH_SHORT).show()
                })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = if (settings.highContrast) Color(0xFFCCCCCC) else Color(0xFFF3F4F6))
                ProfileMenuItemSwitch(icon = Icons.Default.Science, iconBg = Color(0xFFF5F3FF), iconTint = Color(0xFF7C3AED), title = "Contribute to Research", subtitle = if (contributeData) "Your scans help improve DermaLens" else "Help us improve for Filipino skin tones", checked = contributeData, onCheckedChange = {
                    if (it) {
                        showContributeDialog = true
                    } else {
                        contributeData = false
                        prefs.edit().putBoolean(DermaPrefs.KEY_CONTRIBUTE_DATA, false).apply()
                        com.dermalens.app.worker.ContributionUploadScheduler.cancelUpload(context)
                    }
                })
            }
            } }

            Spacer(modifier = Modifier.height(16.dp))

            // App Section
            EntranceAnimation(delayMillis = 120) { Column {
            ProfileSectionHeader("App")
            Spacer(modifier = Modifier.height(8.dp))
            ProfileMenuCard {
                ProfileMenuItem(icon = Icons.Default.History, iconBg = Color(0xFFF5F3FF), iconTint = Color(0xFF7C3AED), title = "Scan History", subtitle = "View all your past scans", onClick = { navController.navigate(Screen.ProgressTracker.route) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = if (settings.highContrast) Color(0xFFCCCCCC) else Color(0xFFF3F4F6))
                ProfileMenuItem(icon = Icons.Default.LocationOn, iconBg = Color(0xFFF0FDF4), iconTint = Color(0xFF16A34A), title = "Find Clinics", subtitle = "Locate nearby dermatologists", onClick = { navController.navigate(Screen.ClinicLocator.route) })
            }
            } }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            EntranceAnimation(delayMillis = 180) { Column {
            ProfileSectionHeader("About")
            Spacer(modifier = Modifier.height(8.dp))
            ProfileMenuCard {
                ProfileMenuItem(icon = Icons.Default.Info, iconBg = Color(0xFFEFF6FF), iconTint = Color(0xFF2563EB), title = "About DermaLens", subtitle = "Version 1.0.0 — Capstone 2026", onClick = { showAboutDialog = true })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = if (settings.highContrast) Color(0xFFCCCCCC) else Color(0xFFF3F4F6))
                ProfileMenuItem(icon = Icons.Default.Shield, iconBg = Color(0xFFF0FDF4), iconTint = Color(0xFF16A34A), title = "Privacy Policy", subtitle = "How we handle your data", onClick = { showPrivacyDialog = true })
            }
            } }

            Spacer(modifier = Modifier.height(16.dp))

            // Accessibility Section
            EntranceAnimation(delayMillis = 240) { Column {
            ProfileSectionHeader("Accessibility")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .then(if (settings.highContrast) Modifier.border(1.dp, Color.Black, RoundedCornerShape(14.dp)) else Modifier),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFF0F0F0) else Color.White),
                elevation = CardDefaults.cardElevation(if (settings.highContrast) 0.dp else 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF5F3FF)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.TextFields, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Font Size", fontSize = settings.textMd.sp, fontWeight = FontWeight.SemiBold, color = settings.textPrimary)
                            Text(when { fontScale <= 0.85f -> "Small"; fontScale <= 1.0f -> "Normal"; fontScale <= 1.15f -> "Large"; else -> "Extra Large" }, fontSize = settings.textBase.sp, color = settings.textSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = fontScale,
                        onValueChange = { fontScale = it; prefs.edit().putFloat(DermaPrefs.KEY_FONT_SIZE, it).apply() },
                        valueRange = 0.75f..1.5f,
                        steps = 2,
                        colors = SliderDefaults.colors(thumbColor = DermaGreen, activeTrackColor = DermaGreen, inactiveTrackColor = DermaGreenLight),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Small", "Normal", "Large", "XL").forEach {
                            Text(it, fontSize = settings.textSm.sp, color = settings.textSecondary)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = if (settings.highContrast) Color(0xFFCCCCCC) else Color(0xFFF3F4F6))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFEF3C7)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Contrast, contentDescription = "High contrast", tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High Contrast", fontSize = settings.textMd.sp, fontWeight = FontWeight.SemiBold, color = settings.textPrimary)
                            Text(if (highContrast) "Enabled" else "Disabled", fontSize = settings.textBase.sp, color = settings.textSecondary)
                        }
                        Switch(
                            checked = highContrast,
                            onCheckedChange = { highContrast = it; prefs.edit().putBoolean(DermaPrefs.KEY_HIGH_CONTRAST, it).apply() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DermaGreen, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFE5E7EB))
                        )
                    }
                }
            }
            } }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { showLogoutDialog = true }
                    .then(if (settings.highContrast) Modifier.border(1.dp, Color(0xFFDC2626), RoundedCornerShape(14.dp)) else Modifier),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFFECACA) else Color(0xFFFEF2F2)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Logout", fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Delete Account -- fulfills the promise already made in the Privacy Policy's "Your
            // Rights" section ("You may delete your account and all associated data at any
            // time"), which had no actual implementation behind it until now.
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable {
                    deleteAccountPassword = ""; deleteAccountError = ""; showDeleteAccountDialog = true
                }.then(if (settings.highContrast) Modifier.border(1.dp, Color(0xFF7F1D1D), RoundedCornerShape(14.dp)) else Modifier),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFFECACA) else Color(0xFFFEF2F2)),
                elevation = CardDefaults.cardElevation(0.dp),
                border = if (settings.highContrast) null else BorderStroke(1.dp, Color(0xFFFECACA))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete Account", tint = Color(0xFF7F1D1D), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Delete Account", fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7F1D1D))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("⚕️ DermaLens is a capstone project by Tarlac State University.\nFor educational and research purposes only.", fontSize = settings.textSm.sp, color = settings.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), lineHeight = 16.sp)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Logout", fontWeight = FontWeight.Bold, fontSize = settings.textXl.sp) },
            text = { Text("Are you sure you want to logout from DermaLens?", color = settings.textPrimary, fontSize = settings.textMd.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        // Firebase's own session is separate from our local KEY_IS_LOGGED_IN
                        // flag -- without this, currentUser stays signed in as the previous
                        // account even after "logging out," which would leak into guest mode
                        // or a different account's session.
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                        context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE).edit().putBoolean(DermaPrefs.KEY_IS_LOGGED_IN, false).apply()
                        navController.navigate(Screen.Login.route) { popUpTo(Screen.Home.route) { inclusive = true } }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Logout", fontSize = settings.textMd.sp) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }, shape = RoundedCornerShape(10.dp)) { Text("Cancel", fontSize = settings.textMd.sp) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            titleContentColor = Color(0xFF111827),
            textContentColor = Color(0xFF374151)
        )
    }

    // Delete Account Dialog -- requires reauthentication (Firebase rejects delete() on a stale
    // session) and deletes local scan records + their image files before removing the Firebase
    // account itself, so this actually fulfills the Privacy Policy's existing "you may delete
    // your account and all associated data at any time" promise instead of leaving it unbuilt.
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFF7F1D1D)) },
            title = { Text("Delete Account", fontWeight = FontWeight.Bold, fontSize = settings.textXl.sp) },
            text = {
                Column {
                    Text(
                        "This permanently deletes your account, scan history, and saved images. This cannot be undone. Enter your password to confirm.",
                        color = settings.textPrimary,
                        fontSize = settings.textMd.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deleteAccountPassword,
                        onValueChange = { deleteAccountPassword = it; deleteAccountError = "" },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !isDeletingAccount,
                        isError = deleteAccountError.isNotEmpty(),
                        supportingText = { if (deleteAccountError.isNotEmpty()) Text(deleteAccountError, color = Color(0xFFDC2626)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deleteAccountPassword.isEmpty()) {
                            deleteAccountError = "Enter your password to confirm."
                            return@Button
                        }
                        isDeletingAccount = true
                        scope.launch {
                            try {
                                val firebaseUser = FirebaseAuth.getInstance().currentUser
                                val prefs = context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                                val savedEmail = prefs.getString(DermaPrefs.KEY_USER_EMAIL, "") ?: ""
                                if (firebaseUser == null || savedEmail.isBlank()) {
                                    deleteAccountError = "Your session has expired. Please log out and back in."
                                    isDeletingAccount = false
                                    return@launch
                                }

                                val credential = EmailAuthProvider.getCredential(savedEmail, deleteAccountPassword)
                                firebaseUser.reauthenticate(credential).awaitTask()

                                val db = DermaDatabase.getDatabase(context)
                                val user = db.userDao().getUserByEmail(savedEmail)
                                if (user != null) {
                                    // Real files on disk, not just DB rows -- "all associated
                                    // data" includes what's actually saved locally.
                                    db.scanRecordDao().getScansByUserOnce(user.userId).forEach { scan ->
                                        if (scan.imagePath.isNotEmpty()) {
                                            try { java.io.File(scan.imagePath).delete() } catch (e: Exception) { }
                                        }
                                        db.scanRecordDao().deleteScan(scan.id)
                                    }
                                    db.userDao().deleteUserById(user.userId)
                                }

                                firebaseUser.delete().awaitTask()

                                prefs.edit()
                                    .putBoolean(DermaPrefs.KEY_IS_LOGGED_IN, false)
                                    .remove(DermaPrefs.KEY_USER_EMAIL)
                                    .apply()

                                showDeleteAccountDialog = false
                                isDeletingAccount = false
                                navController.navigate(Screen.Login.route) { popUpTo(Screen.Home.route) { inclusive = true } }
                            } catch (e: Exception) {
                                deleteAccountError = firebaseAuthErrorMessage(e)
                                isDeletingAccount = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isDeletingAccount
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete Account", fontSize = settings.textMd.sp)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAccountDialog = false }, shape = RoundedCornerShape(10.dp), enabled = !isDeletingAccount) { Text("Cancel", fontSize = settings.textMd.sp) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            titleContentColor = Color(0xFF111827),
            textContentColor = Color(0xFF374151)
        )
    }

    // Contribute to Research Dialog
    if (showContributeDialog) {
        AlertDialog(
            onDismissRequest = { showContributeDialog = false },
            icon = { Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFF7C3AED)) },
            title = { Text("Contribute to Research", fontWeight = FontWeight.Bold, fontSize = settings.textXl.sp, color = Color(0xFF111827)) },
            text = {
                Text(
                    "When enabled, your skin scan images and results will be added to the DermaLens research dataset. This data helps train and improve future versions of the AI model, especially for detecting conditions across a wider range of Filipino skin tones.\n\nYour scans are contributed anonymously and are never linked to your name or account. You can turn this off at any time.",
                    fontSize = settings.textMd.sp,
                    color = Color(0xFF374151),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        contributeData = true
                        prefs.edit().putBoolean(DermaPrefs.KEY_CONTRIBUTE_DATA, true).apply()
                        com.dermalens.app.worker.ContributionUploadScheduler.scheduleUpload(context)
                        showContributeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Allow", fontSize = settings.textMd.sp) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showContributeDialog = false }, shape = RoundedCornerShape(10.dp)) { Text("Not Now", fontSize = settings.textMd.sp) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            titleContentColor = Color(0xFF111827),
            textContentColor = Color(0xFF374151)
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF16A34A)) },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold, fontSize = settings.textXl.sp, color = Color(0xFF111827)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Effective Date: January 1, 2026", fontSize = settings.textSm.sp, color = Color(0xFF6B7280))
                    PrivacySection("1. Information We Collect", "DermaLens collects the following data to provide its services:\n\n• Profile information you provide (full name, email address)\n• Skin scan results including detected condition, confidence score, and severity level\n• Scan history and timestamps stored locally on your device\n• Device location (GPS) used only to find nearby dermatology clinics")
                    PrivacySection("2. How We Use Your Data", "All data collected by DermaLens is used solely to:\n\n• Display your scan history and progress over time\n• Personalize your in-app experience\n• Help locate nearby dermatology clinics based on your location\n• Send optional daily skin care reminder notifications")
                    PrivacySection("3. Data Storage", "All personal data and scan records are stored locally on your device using a secure Room database. DermaLens does not transmit your personal information or scan images to any external server or cloud service.")
                    PrivacySection("4. Camera & Gallery Access", "Camera and gallery access is used exclusively to capture or select skin images for AI analysis. Images are processed on-device and are never uploaded, stored permanently, or shared with third parties.")
                    PrivacySection("5. Location Access", "Location is accessed only when you use the Clinic Locator feature to find nearby dermatology clinics. Location data is not stored or logged.")
                    PrivacySection("6. AI Disclaimer", "DermaLens uses an on-device AI model (YOLOv11 TFLite) for skin condition detection. Results are for reference only and do not constitute medical advice. Always consult a dermatologist for diagnosis and treatment.")
                    PrivacySection("7. Children's Privacy", "DermaLens is not intended for users under the age of 13. We do not knowingly collect data from children.")
                    PrivacySection("8. Contact", "DermaLens is a capstone project developed at Tarlac State University, 2026. For questions or concerns, please contact the development team through your institution.")
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)), shape = RoundedCornerShape(10.dp)) {
                    Text("Got it", fontSize = settings.textMd.sp)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            titleContentColor = Color(0xFF111827),
            textContentColor = Color(0xFF374151)
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Image(painter = painterResource(id = R.drawable.dermalens_logo), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))) },
            title = { Text("DermaLens", fontWeight = FontWeight.Bold, color = DermaGreen, fontSize = settings.textXl.sp) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Version 1.0.0", fontSize = settings.textBase.sp, color = Color(0xFF6B7280))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("An Android-based skin disease detection system using YOLOv11 TFLite. Developed as a Capstone Project at Tarlac State University, 2026.", fontSize = settings.textBase.sp, color = Color(0xFF374151), textAlign = TextAlign.Center, lineHeight = 20.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Developed by:", fontSize = settings.textBase.sp, color = Color(0xFF6B7280))
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf("Mark Joseph Garcia", "Reynaldo Manio Jr.", "Reicee Owen Pastrana", "Chrisent Dayniel Tolentino").forEach {
                        Text(it, fontSize = settings.textMd.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = DermaGreen), shape = RoundedCornerShape(10.dp)) { Text("Close", fontSize = settings.textMd.sp) }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            titleContentColor = Color(0xFF111827),
            textContentColor = Color(0xFF374151)
        )
    }
}

// ── Edit Profile Screen ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(navController: NavController) {
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var saveTrigger by remember { mutableStateOf(0) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DermaGreen, focusedLabelColor = DermaGreen,
        unfocusedBorderColor = if (settings.highContrast) Color.Black else Color(0xFFE5E7EB),
        unfocusedLabelColor = if (settings.highContrast) Color(0xFF1a1a1a) else Color(0xFF9CA3AF),
        focusedTextColor = Color(0xFF111827), unfocusedTextColor = Color(0xFF111827)
    )

    LaunchedEffect(Unit) {
        val db = DermaDatabase.getDatabase(context)
        val prefs = context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val savedEmail = prefs.getString(DermaPrefs.KEY_USER_EMAIL, "") ?: ""
        val user = db.userDao().getUserByEmail(savedEmail)
        if (user != null) { name = user.fullName; email = user.email }
    }

    LaunchedEffect(saveTrigger) {
        if (saveTrigger == 0) return@LaunchedEffect
        errorMessage = ""
        val db = DermaDatabase.getDatabase(context)
        val prefs = context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val savedEmail = prefs.getString(DermaPrefs.KEY_USER_EMAIL, "") ?: ""
        val user = db.userDao().getUserByEmail(savedEmail)
        if (user != null) {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) { errorMessage = "Full name cannot be empty."; return@LaunchedEffect }
            // Email is not edited here: changing it safely needs Firebase's verify-before-
            // update-email flow (a second confirmation link to the new address), which is a
            // bigger feature not wired up yet. Password changes *are* wired up below via
            // Firebase reauthentication.
            if (newPassword.isNotEmpty()) {
                if (currentPassword.isEmpty()) { errorMessage = "Enter your current password to set a new one."; return@LaunchedEffect }
                if (newPassword != confirmPassword) { errorMessage = "New passwords do not match."; return@LaunchedEffect }
                if (!isStrongPassword(newPassword)) {
                    errorMessage = "Must be 8+ characters with an uppercase letter, lowercase letter, number, and special character."
                    return@LaunchedEffect
                }

                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser == null) { errorMessage = "Your session has expired. Please log out and back in."; return@LaunchedEffect }
                try {
                    val credential = EmailAuthProvider.getCredential(user.email, currentPassword)
                    firebaseUser.reauthenticate(credential).awaitTask()
                    firebaseUser.updatePassword(newPassword).awaitTask()
                } catch (e: Exception) {
                    errorMessage = firebaseAuthErrorMessage(e)
                    return@LaunchedEffect
                }
                currentPassword = ""; newPassword = ""; confirmPassword = ""
            }
            db.userDao().updateProfile(user.userId, trimmedName, user.email)
            isSaved = true
        }
    }

    Scaffold(
        containerColor = if (settings.highContrast) Color.White else Color(0xFFF8F9FA),
        topBar = {
            DermaGlassTopBar(
                title = "Edit Profile",
                onBack = { navController.popBackStack() },
                titleColor = settings.textPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().background(if (settings.highContrast) Color.White else Color(0xFFF8F9FA)).padding(innerPadding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(DermaGreenLight)
                    .border(2.5.dp, DermaGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(name.split(" ").filter { it.isNotEmpty() }.take(2).map { it.first() }.joinToString("").ifEmpty { "?" }, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DermaGreen)
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Profile Info
            Card(modifier = Modifier.fillMaxWidth().then(if (settings.highContrast) Modifier.border(1.dp, Color.Black, RoundedCornerShape(16.dp)) else Modifier), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFF0F0F0) else Color.White), elevation = CardDefaults.cardElevation(if (settings.highContrast) 0.dp else 2.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Profile Info", fontSize = settings.textBase.sp, fontWeight = FontWeight.SemiBold, color = settings.textSecondary)
                    OutlinedTextField(value = name, onValueChange = { name = it; isSaved = false }, label = { Text("Full name") }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                    OutlinedTextField(
                        value = email,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Email address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Account security -- password changes go through Firebase reauthentication
            // (re-enter current password first).
            Card(modifier = Modifier.fillMaxWidth().then(if (settings.highContrast) Modifier.border(1.dp, Color.Black, RoundedCornerShape(16.dp)) else Modifier), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFF0F0F0) else Color.White), elevation = CardDefaults.cardElevation(if (settings.highContrast) 0.dp else 2.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Account Security", fontSize = settings.textBase.sp, fontWeight = FontWeight.SemiBold, color = settings.textSecondary)
                    Text("Leave blank to keep your current password", fontSize = settings.textSm.sp, color = settings.textSecondary)
                    OutlinedTextField(value = currentPassword, onValueChange = { currentPassword = it; isSaved = false; errorMessage = "" }, label = { Text("Current password") }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }, trailingIcon = { IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) { Icon(if (showCurrentPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showCurrentPassword) "Hide password" else "Show password") } }, visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                    OutlinedTextField(value = newPassword, onValueChange = { newPassword = it; isSaved = false; errorMessage = "" }, label = { Text("New password") }, leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }, trailingIcon = { IconButton(onClick = { showNewPassword = !showNewPassword }) { Icon(if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showNewPassword) "Hide password" else "Show password") } }, visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                    OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it; isSaved = false; errorMessage = "" }, label = { Text("Confirm new password") }, leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }, trailingIcon = { IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) { Icon(if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showConfirmPassword) "Hide password" else "Show password") } }, visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                }
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMessage, color = Color(0xFFDC2626), fontSize = settings.textBase.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { saveTrigger++ }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isSaved) Color(0xFF16A34A) else DermaGreen)) {
                Icon(if (isSaved) Icons.Default.Check else Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isSaved) "Saved!" else "Save Changes", fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, if (settings.highContrast) Color.Black else Color(0xFFE5E7EB))) {
                Text("Cancel", color = settings.textPrimary, fontSize = settings.textLg.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Helper Composables ────────────────────────────────────────────────────────
@Composable
fun PrivacySection(title: String, body: String) {
    val settings = LocalAppSettings.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = settings.textBase.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
        Text(body, fontSize = settings.textSm.sp, color = Color(0xFF6B7280), lineHeight = 18.sp)
    }
}

@Composable
fun ProfileSectionHeader(title: String) {
    val settings = LocalAppSettings.current
    Text(title.uppercase(), fontSize = settings.textSm.sp, fontWeight = FontWeight.Bold, color = settings.textSecondary, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun ProfileStatItem(value: String, label: String, icon: String) {
    val settings = LocalAppSettings.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = settings.textXxl.sp, fontWeight = FontWeight.Bold, color = DermaGreen)
        Text(label, fontSize = settings.textSm.sp, color = settings.textSecondary)
    }
}

@Composable
fun ProfileMenuCard(content: @Composable ColumnScope.() -> Unit) {
    val settings = LocalAppSettings.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .then(if (settings.highContrast) Modifier.border(1.dp, Color.Black, RoundedCornerShape(14.dp)) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFF0F0F0) else Color.White),
        elevation = CardDefaults.cardElevation(if (settings.highContrast) 0.dp else 2.dp)
    ) {
        Column { content() }
    }
}

@Composable
fun ProfileMenuItem(icon: ImageVector, iconBg: Color, iconTint: Color, title: String, subtitle: String, onClick: () -> Unit = {}) {
    val settings = LocalAppSettings.current
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = settings.textMd.sp, fontWeight = FontWeight.SemiBold, color = settings.textPrimary)
            Text(subtitle, fontSize = settings.textBase.sp, color = settings.textSecondary)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = if (settings.highContrast) Color(0xFF666666) else Color(0xFFD1D5DB), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ProfileMenuItemSwitch(icon: ImageVector, iconBg: Color, iconTint: Color, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val settings = LocalAppSettings.current
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = settings.textMd.sp, fontWeight = FontWeight.SemiBold, color = settings.textPrimary)
            Text(subtitle, fontSize = settings.textBase.sp, color = settings.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DermaGreen, uncheckedThumbColor = Color.White, uncheckedTrackColor = if (settings.highContrast) Color(0xFF888888) else Color(0xFFE5E7EB)))
    }
}