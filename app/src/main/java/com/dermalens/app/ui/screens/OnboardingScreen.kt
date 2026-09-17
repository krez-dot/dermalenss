package com.dermalens.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dermalens.app.R
import com.dermalens.app.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val backgroundColor: Color,
    val accentColor: Color
)

val onboardingPages = listOf(
    OnboardingPage("Detect Skin Conditions", "DermaLens uses advanced YOLOv11 AI to detect 6 common skin conditions instantly — right from your phone camera.", Icons.Default.Search, Color(0xFF7C3AED), Color(0xFFEDE9FE)),
    OnboardingPage("Just Point & Scan", "Simply point your camera at the affected skin area and tap scan. Get results in seconds — no internet required!", Icons.Default.CameraAlt, Color(0xFF0284C7), Color(0xFFE0F2FE)),
    OnboardingPage("Track Your Progress", "Monitor your skin's improvement over time with detailed timelines, the Family Tree condition guide, and nearby clinic recommendations.", Icons.Default.Timeline, Color(0xFF0D9488), Color(0xFFCCFBF1)),
    OnboardingPage("Stay Informed", "Learn more about your condition — Acne, Eczema, Melasma, Tinea, Warts, and Scabies — all explained in one app.", Icons.Default.LocalHospital, Color(0xFF7C3AED), Color(0xFFEDE9FE))
)

// ── Splash Screen ─────────────────────────────────────────────────────────────
@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    val hasSeenOnboarding = remember { prefs.getBoolean(DermaPrefs.KEY_HAS_SEEN_ONBOARDING, false) }
    val isLoggedIn = remember { prefs.getBoolean(DermaPrefs.KEY_IS_LOGGED_IN, false) }

    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { alpha.animateTo(1f, animationSpec = tween(800)) }
        delay(2000)
        when {
            isLoggedIn -> navController.navigate(Screen.Home.route) { popUpTo(Screen.Splash.route) { inclusive = true } }
            hasSeenOnboarding -> navController.navigate(Screen.Login.route) { popUpTo(Screen.Splash.route) { inclusive = true } }
            else -> navController.navigate(Screen.Onboarding.route) { popUpTo(Screen.Splash.route) { inclusive = true } }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(DermaGreen, DermaGreenDark))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.dermalens_logo),
                contentDescription = "DermaLens logo",
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(28.dp))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("DermaLens", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Skin health in your hands", fontSize = 15.sp, color = Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
        Text("Tarlac State University • Capstone 2026", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
    }
}

// ── Onboarding Screen ─────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(navController: NavController) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()

    fun finishOnboarding() {
        context.getSharedPreferences(DermaPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DermaPrefs.KEY_HAS_SEEN_ONBOARDING, true)
            .apply()
        navController.navigate(Screen.Login.route) { popUpTo(Screen.Onboarding.route) { inclusive = true } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            OnboardingPageContent(page = onboardingPages[page])
        }

        TextButton(onClick = { finishOnboarding() }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Text("Skip", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(onboardingPages.size) { index ->
                    Box(
                        modifier = Modifier.animateContentSize().height(8.dp)
                            .width(if (pagerState.currentPage == index) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = 0.4f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (pagerState.currentPage < onboardingPages.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        finishOnboarding()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(
                    if (pagerState.currentPage < onboardingPages.size - 1) "Next →" else "Get Started 🚀",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = onboardingPages[pagerState.currentPage].backgroundColor
                )
            }
        }
    }
}

// ── Onboarding Page Content ───────────────────────────────────────────────────
@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(page.backgroundColor, page.backgroundColor.copy(alpha = 0.85f)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Box(
                modifier = Modifier.size(160.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = page.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(80.dp))
            }
            Spacer(modifier = Modifier.height(48.dp))
            Text(text = page.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, lineHeight = 36.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = page.description, fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, lineHeight = 24.sp)
        }
    }
}