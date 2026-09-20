package com.dermalens.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dermalens.app.R
import com.dermalens.app.data.db.DermaDatabase
import com.dermalens.app.data.model.User
import com.dermalens.app.navigation.Screen
import com.dermalens.app.ui.LocalAppSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.launch

/**
 * Real format validation (was previously just `email.contains("@")`, which let through
 * anything with an @ in it, e.g. "a@b"). Android's built-in pattern is the standard
 * practical check -- not full RFC 5322, but catches the actual garbage inputs that matter.
 */
fun isValidEmail(email: String): Boolean =
    email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

/** Only enforced at Register -- Login just needs 6+ characters (see LoginScreen's own validate()),
 *  since an existing account's password was created under whatever rules were live at the time
 *  and Firebase, not this check, is the real authority on whether it's correct. Requiring 8+ chars
 *  with upper/lower/digit/special here only shapes *new* passwords going forward. */
fun isStrongPassword(password: String): Boolean =
    password.length >= 8 &&
        password.any { it.isUpperCase() } &&
        password.any { it.isLowerCase() } &&
        password.any { it.isDigit() } &&
        password.any { !it.isLetterOrDigit() }

/** Translates Firebase Auth's exception types into short, user-facing messages, rather than
 * surfacing Firebase's raw internal wording. Used by both the auth screens and Edit Profile's
 * password-change flow. */
fun firebaseAuthErrorMessage(e: Exception): String = when (e) {
    is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 6 characters."
    is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password. Please try again."
    is FirebaseAuthUserCollisionException -> "An account with this email already exists."
    is FirebaseAuthInvalidUserException -> "No account found with this email, or it has been disabled."
    else -> e.localizedMessage ?: "Something went wrong. Please try again."
}

@Composable
private fun dermaFieldColors(highContrast: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DermaGreen,
    focusedLabelColor = DermaGreen,
    unfocusedBorderColor = if (highContrast) Color(0xFF000000) else Color(0xFFE5E7EB),
    unfocusedLabelColor = if (highContrast) Color(0xFF1a1a1a) else Color(0xFF9CA3AF),
    focusedTextColor = Color(0xFF111827),
    unfocusedTextColor = Color(0xFF111827)
)

// ── Login Screen ──────────────────────────────────────────────────────────────
@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val db = remember { DermaDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    val settings = LocalAppSettings.current

    var email by remember { mutableStateOf(prefs.getString(DermaPrefs.KEY_REMEMBER_EMAIL, "") ?: "") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf("") }
    var infoMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(email.isNotEmpty()) }

    val bgColor = settings.background
    val cardBg = if (settings.highContrast) Color(0xFFF0F0F0) else Color(0xFFFEE2E2)
    val infoCardBg = if (settings.highContrast) Color(0xFFF0F0F0) else Color(0xFFDCFCE7)

    fun validate(): Boolean {
        var valid = true
        if (!isValidEmail(email)) { emailError = "Please enter a valid email address"; valid = false } else emailError = ""
        if (password.length < 6) { passwordError = "Password must be at least 6 characters"; valid = false } else passwordError = ""
        return valid
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp).verticalScroll(rememberScrollState()).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            Image(
                painter = painterResource(id = R.drawable.dermalens_logo),
                contentDescription = "DermaLens logo",
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp))
                    .then(if (settings.highContrast) Modifier.border(2.dp, Color.Black, RoundedCornerShape(24.dp)) else Modifier)
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text("Welcome back", fontSize = settings.textDisplay.sp, fontWeight = FontWeight.Bold, color = settings.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Sign in to continue to DermaLens", fontSize = settings.textMd.sp, color = settings.textSecondary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(40.dp))

            if (loginError.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cardBg).padding(12.dp).semantics { contentDescription = "Error: $loginError" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(loginError, fontSize = settings.textBase.sp, color = Color(0xFFDC2626))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (infoMessage.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(infoCardBg).padding(12.dp).semantics { contentDescription = infoMessage },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(infoMessage, fontSize = settings.textBase.sp, color = Color(0xFF166534))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = email, onValueChange = { email = it; emailError = ""; loginError = ""; infoMessage = "" },
                label = { Text("Email address") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                isError = emailError.isNotEmpty(),
                supportingText = { if (emailError.isNotEmpty()) Text(emailError, color = Color(0xFFDC2626)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Email address input field" },
                shape = RoundedCornerShape(14.dp), colors = dermaFieldColors(settings.highContrast)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password, onValueChange = { password = it; passwordError = ""; loginError = "" },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (passwordVisible) "Hide password" else "Show password", tint = Color(0xFF9CA3AF))
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = passwordError.isNotEmpty(),
                supportingText = { if (passwordError.isNotEmpty()) Text(passwordError, color = Color(0xFFDC2626)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Password input field" },
                shape = RoundedCornerShape(14.dp), colors = dermaFieldColors(settings.highContrast)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberMe, onCheckedChange = { rememberMe = it },
                        colors = CheckboxDefaults.colors(checkedColor = DermaGreen, uncheckedColor = if (settings.highContrast) Color.Black else Color(0xFF9CA3AF))
                    )
                    Text("Remember me", fontSize = settings.textMd.sp, color = settings.textPrimary)
                }
                TextButton(onClick = {
                    infoMessage = ""
                    if (!isValidEmail(email)) {
                        loginError = "Enter your email above first, then tap \"Forgot password?\""
                    } else {
                        loginError = ""
                        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim())
                            .addOnSuccessListener { infoMessage = "Password reset email sent to ${email.trim()}." }
                            .addOnFailureListener { e -> loginError = firebaseAuthErrorMessage(e) }
                    }
                }) {
                    Text("Forgot password?", fontSize = settings.textSm.sp, color = DermaGreen, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (validate()) {
                        isLoading = true
                        FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password)
                            .addOnSuccessListener { result ->
                                val firebaseUser = result.user
                                scope.launch {
                                    var user = firebaseUser?.uid?.let { db.userDao().getUserByFirebaseUid(it) }
                                    if (user == null && firebaseUser != null) {
                                        // No local profile yet (fresh install / different device)
                                        // -- create a minimal one so the rest of the app has a
                                        // profile row to read.
                                        db.userDao().insertUser(
                                            User(
                                                fullName = firebaseUser.email?.substringBefore("@") ?: "User",
                                                email = email.trim(),
                                                passwordHash = "",
                                                firebaseUid = firebaseUser.uid
                                            )
                                        )
                                    }
                                    isLoading = false
                                    if (firebaseUser != null && !firebaseUser.isEmailVerified) {
                                        navController.navigate(Screen.VerifyEmail.route) { popUpTo(Screen.Login.route) { inclusive = true } }
                                        return@launch
                                    }
                                    prefs.edit().apply {
                                        if (rememberMe) putString(DermaPrefs.KEY_REMEMBER_EMAIL, email.trim()) else remove(DermaPrefs.KEY_REMEMBER_EMAIL)
                                        putBoolean(DermaPrefs.KEY_IS_LOGGED_IN, true)
                                        putString(DermaPrefs.KEY_USER_EMAIL, email.trim())
                                        apply()
                                    }
                                    navController.navigate(Screen.Home.route) { popUpTo(Screen.Login.route) { inclusive = true } }
                                }
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                loginError = firebaseAuthErrorMessage(e)
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp).semantics { contentDescription = "Sign in button" },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DermaGreen),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Sign In", fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = if (settings.highContrast) Color.Black else Color(0xFFE5E7EB))
                Text("  or  ", fontSize = settings.textBase.sp, color = settings.textSecondary)
                HorizontalDivider(modifier = Modifier.weight(1f), color = if (settings.highContrast) Color.Black else Color(0xFFE5E7EB))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Don't have an account?", fontSize = settings.textMd.sp, color = settings.textSecondary)
                TextButton(onClick = { navController.navigate(Screen.Register.route) }) {
                    Text("Sign Up", fontSize = settings.textMd.sp, color = DermaGreen, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            DiagnosticAidDisclaimer()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Register Screen ───────────────────────────────────────────────────────────
@Composable
fun RegisterScreen(navController: NavController) {
    val context = LocalContext.current
    val db = remember { DermaDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val settings = LocalAppSettings.current

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var confirmPasswordError by remember { mutableStateOf("") }
    var registerError by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        var valid = true
        if (name.isBlank()) { nameError = "Name is required"; valid = false } else nameError = ""
        if (!isValidEmail(email)) { emailError = "Enter a valid email"; valid = false } else emailError = ""
        if (!isStrongPassword(password)) {
            passwordError = "Must be 8+ characters with an uppercase letter, lowercase letter, number, and special character"
            valid = false
        } else passwordError = ""
        if (confirmPassword != password) { confirmPasswordError = "Passwords do not match"; valid = false } else confirmPasswordError = ""
        return valid
    }

    Box(modifier = Modifier.fillMaxSize().background(settings.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp).verticalScroll(rememberScrollState()).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            Box(
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp))
                    .background(DermaGreenLight)
                    .then(if (settings.highContrast) Modifier.border(2.dp, Color.Black, RoundedCornerShape(24.dp)) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Create account icon", tint = DermaGreen, modifier = Modifier.size(44.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Create account", fontSize = settings.textDisplay.sp, fontWeight = FontWeight.Bold, color = settings.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Join DermaLens today", fontSize = settings.textMd.sp, color = settings.textSecondary)
            Spacer(modifier = Modifier.height(32.dp))

            if (registerError.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFEE2E2)).padding(12.dp).semantics { contentDescription = "Error: $registerError" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(registerError, fontSize = settings.textBase.sp, color = Color(0xFFDC2626))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(value = name, onValueChange = { name = it; nameError = "" }, label = { Text("Full name") }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }, isError = nameError.isNotEmpty(), supportingText = { if (nameError.isNotEmpty()) Text(nameError, color = Color(0xFFDC2626)) }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Full name input field" }, shape = RoundedCornerShape(14.dp), colors = dermaFieldColors(settings.highContrast))
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = email, onValueChange = { email = it; emailError = ""; registerError = "" }, label = { Text("Email address") }, leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }, isError = emailError.isNotEmpty(), supportingText = { if (emailError.isNotEmpty()) Text(emailError, color = Color(0xFFDC2626)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Email address input field" }, shape = RoundedCornerShape(14.dp), colors = dermaFieldColors(settings.highContrast))
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = password, onValueChange = { password = it; passwordError = "" }, label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }, trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (passwordVisible) "Hide password" else "Show password", tint = Color(0xFF9CA3AF)) } }, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), isError = passwordError.isNotEmpty(), supportingText = { if (passwordError.isNotEmpty()) Text(passwordError, color = Color(0xFFDC2626)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Password input field" }, shape = RoundedCornerShape(14.dp), colors = dermaFieldColors(settings.highContrast))
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it; confirmPasswordError = "" }, label = { Text("Confirm password") }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }, trailingIcon = { IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) { Icon(if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password", tint = Color(0xFF9CA3AF)) } }, visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(), isError = confirmPasswordError.isNotEmpty(), supportingText = { if (confirmPasswordError.isNotEmpty()) Text(confirmPasswordError, color = Color(0xFFDC2626)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Confirm password input field" }, shape = RoundedCornerShape(14.dp), colors = dermaFieldColors(settings.highContrast))

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (validate()) {
                        isLoading = true
                        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password)
                            .addOnSuccessListener { result ->
                                val firebaseUser = result.user
                                firebaseUser?.sendEmailVerification()
                                scope.launch {
                                    try {
                                        db.userDao().insertUser(
                                            User(
                                                fullName = name.trim(),
                                                email = email.trim(),
                                                passwordHash = "",
                                                firebaseUid = firebaseUser?.uid
                                            )
                                        )
                                        isLoading = false
                                        navController.navigate(Screen.VerifyEmail.route) { popUpTo(Screen.Register.route) { inclusive = true } }
                                    } catch (e: android.database.sqlite.SQLiteConstraintException) {
                                        registerError = "An account with this email already exists."
                                        isLoading = false
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                registerError = firebaseAuthErrorMessage(e)
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp).semantics { contentDescription = "Create account button" },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DermaGreen),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Create Account", fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?", fontSize = settings.textMd.sp, color = settings.textSecondary)
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Sign In", fontSize = settings.textMd.sp, color = DermaGreen, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = { showPrivacyDialog = true }, contentPadding = PaddingValues(0.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = settings.textSecondary)) { append("By registering, you agree to our ") }
                        withStyle(SpanStyle(color = DermaGreen, fontWeight = FontWeight.SemiBold)) { append("Privacy Policy") }
                    },
                    fontSize = settings.textSm.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            DiagnosticAidDisclaimer()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = Color.White,
            titleContentColor = Color(0xFF111827),
            textContentColor = Color(0xFF374151),
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    PrivacySection("Data We Collect", "We collect your name, email address, and skin scan images you choose to submit. Scan results including condition, severity, and confidence scores are stored locally on your device.")
                    PrivacySection("How We Use Your Data", "Your data is used solely to provide personalized skin health tracking within the app. If you enable 'Contribute to Research', anonymized scan data may be used to improve our detection model.")
                    PrivacySection("Data Storage", "Scan history, results, and app preferences are stored locally on your device. Your email and password are managed by Firebase Authentication (Google's infrastructure) for account verification and sign-in -- your password is never visible to us in plain text. We do not sell, rent, or share your personal information with third parties.")
                    PrivacySection("Research Contributions", "Contribution is entirely opt-in. You may toggle this off at any time in Profile > Contribute to Research. If enabled, contributed images are uploaded to secure cloud storage over Wi-Fi for use in improving the detection model -- the upload is anonymous and contains no name, email, or account information, only the image and its detected condition.")
                    PrivacySection("Your Rights", "You may delete your account and all associated data at any time. Scan records can be individually deleted from the Progress Tracker.")
                    PrivacySection("Medical Disclaimer", "DermaLens is for informational reference only and does not constitute medical advice. Always consult a dermatologist for diagnosis and treatment.")
                    PrivacySection("Contact", "For privacy concerns, contact us through the app's feedback channel.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close", color = DermaGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
fun VerifyEmailScreen(navController: NavController) {
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE) }

    var isChecking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var infoMessage by remember { mutableStateOf("") }
    val email = FirebaseAuth.getInstance().currentUser?.email ?: ""

    Box(modifier = Modifier.fillMaxSize().background(settings.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(DermaGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MarkEmailUnread, contentDescription = null, tint = DermaGreen, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Verify Your Email", fontSize = settings.textXxl.sp, fontWeight = FontWeight.Bold, color = settings.textPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "We sent a verification link to $email. Check your inbox (and spam folder), tap the link, then come back and continue.",
                fontSize = settings.textMd.sp,
                color = settings.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = Color(0xFFDC2626), fontSize = settings.textBase.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (infoMessage.isNotEmpty()) {
                Text(infoMessage, color = Color(0xFF16A34A), fontSize = settings.textBase.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    errorMessage = ""; infoMessage = ""; isChecking = true
                    val firebaseUser = FirebaseAuth.getInstance().currentUser
                    firebaseUser?.reload()
                        ?.addOnSuccessListener {
                            isChecking = false
                            if (firebaseUser.isEmailVerified) {
                                prefs.edit().apply {
                                    putBoolean(DermaPrefs.KEY_IS_LOGGED_IN, true)
                                    putString(DermaPrefs.KEY_USER_EMAIL, firebaseUser.email ?: "")
                                    apply()
                                }
                                NewUserSignal.pendingContributePrompt = true
                                navController.navigate(Screen.Home.route) { popUpTo(Screen.VerifyEmail.route) { inclusive = true } }
                            } else {
                                errorMessage = "Still not verified — tap the link in the email first."
                            }
                        }
                        ?.addOnFailureListener { e ->
                            isChecking = false
                            errorMessage = firebaseAuthErrorMessage(e)
                        }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DermaGreen),
                enabled = !isChecking
            ) {
                if (isChecking) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("I've Verified — Continue", fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    errorMessage = ""; infoMessage = ""
                    FirebaseAuth.getInstance().currentUser?.sendEmailVerification()
                        ?.addOnSuccessListener { infoMessage = "Verification email resent." }
                        ?.addOnFailureListener { e -> errorMessage = firebaseAuthErrorMessage(e) }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Resend Email", fontSize = settings.textLg.sp, fontWeight = FontWeight.SemiBold, color = settings.textPrimary)
            }

            Spacer(modifier = Modifier.height(20.dp))

            TextButton(onClick = {
                FirebaseAuth.getInstance().signOut()
                navController.navigate(Screen.Login.route) { popUpTo(Screen.VerifyEmail.route) { inclusive = true } }
            }) {
                Text("Log Out", fontSize = settings.textMd.sp, color = settings.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}