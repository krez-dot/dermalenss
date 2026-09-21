package com.dermalens.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dermalens.app.ui.screens.*

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Register : Screen("register")
    object VerifyEmail : Screen("verify_email")
    object Home : Screen("home")
    object Scan : Screen("scan?continueTrackGroupId={continueTrackGroupId}") {
        // continueTrackGroupId defaults to -1 ("start a new, unrelated trend even if the result
        // ends up sharing a condition with an existing one") so every existing call site (Home,
        // the bottom nav tab, Progress Tracker's own "Start New Scan") keeps working unchanged.
        // Only Progress Tracker's per-condition "Scan Again" passes a real value, to explicitly
        // continue that specific trend regardless of what this new scan classifies as.
        fun createRoute(continueTrackGroupId: Int = -1) = "scan?continueTrackGroupId=$continueTrackGroupId"
    }
    object ScanResult : Screen("scan_result?imageUri={imageUri}&scanId={scanId}&continueTrackGroupId={continueTrackGroupId}") {
        // scanId defaults to -1 ("not viewing history") so the two existing call sites (a fresh
        // scan from CameraScreen, which only ever passes imageUri) don't need to change at all.
        // Passing a real scanId (from Progress Tracker) tells ScanResultScreen to load that
        // saved record instead of running a brand new inference on the image. continueTrackGroupId
        // just carries Scan's own value forward so it's still known at the point where saving
        // actually happens -- see saveScan().
        fun createRoute(imageUri: String?, scanId: Int = -1, continueTrackGroupId: Int = -1) =
            "scan_result?imageUri=${imageUri?.let { android.net.Uri.encode(it) } ?: ""}&scanId=$scanId&continueTrackGroupId=$continueTrackGroupId"
    }
    object ProgressTracker : Screen("progress_tracker")
    object FamilyTree : Screen("family_tree/{condition}") {
        fun createRoute(condition: String) = "family_tree/${android.net.Uri.encode(condition)}"
    }
    object ClinicLocator : Screen("clinic_locator")
    object Profile : Screen("profile")
    object EditProfile : Screen("edit_profile")
}

@Composable
fun DermaLensNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        // A real push/pop slide, not a fade with a subtle nudge -- the incoming screen slides
        // in from off-screen and the outgoing one slides out a third of the way (parallax),
        // matching the standard Android/iOS "push" navigation feel.
        enterTransition = { slideInHorizontally(tween(320), initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(tween(320), targetOffsetX = { -it / 3 }) },
        popEnterTransition = { slideInHorizontally(tween(320), initialOffsetX = { -it / 3 }) },
        popExitTransition = { slideOutHorizontally(tween(320), targetOffsetX = { it }) }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(navController = navController)
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(navController = navController)
        }
        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(Screen.Register.route) {
            RegisterScreen(navController = navController)
        }
        composable(Screen.VerifyEmail.route) {
            VerifyEmailScreen(navController = navController)
        }
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(
            Screen.Scan.route,
            arguments = listOf(
                navArgument("continueTrackGroupId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            ScanScreen(
                navController = navController,
                continueTrackGroupId = backStackEntry.arguments?.getInt("continueTrackGroupId") ?: -1
            )
        }
        composable(
            Screen.ScanResult.route,
            arguments = listOf(
                navArgument("imageUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("scanId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("continueTrackGroupId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            ScanResultScreen(
                navController = navController,
                imageUri = backStackEntry.arguments?.getString("imageUri"),
                scanId = backStackEntry.arguments?.getInt("scanId") ?: -1,
                continueTrackGroupId = backStackEntry.arguments?.getInt("continueTrackGroupId") ?: -1
            )
        }
        composable(Screen.ProgressTracker.route) {
            ProgressTrackerScreen(navController = navController)
        }
        composable(
            Screen.FamilyTree.route,
            arguments = listOf(navArgument("condition") { type = NavType.StringType })
        ) { backStackEntry ->
            FamilyTreeScreen(
                navController = navController,
                condition = backStackEntry.arguments?.getString("condition") ?: ""
            )
        }
        composable(Screen.ClinicLocator.route) {
            ClinicLocatorScreen(navController = navController)
        }
        composable(Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }
        composable(Screen.EditProfile.route) {
            EditProfileScreen(navController = navController)
        }
    }
}