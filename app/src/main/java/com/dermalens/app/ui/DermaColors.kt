package com.dermalens.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dermalens.app.ui.LocalAppSettings

val DermaGreen = Color(0xFF7C3AED)
val DermaGreenLight = Color(0xFFEDE9FE)
val DermaGreenDark = Color(0xFF6D28D9)


/**
 * Scales a clickable element down slightly while pressed. Used in place of Material's default
 * ripple/indication on icons where that ripple was disabled (see [DermaGlassTopBar] and the
 * bottom nav) -- those had to lose the default indication to work around a background-painting
 * bug in IconButton/NavigationBarItem, but that left the icons with zero tap feedback at all.
 */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.82f else 1f, label = "pressScale")
    return this.graphicsLayer(scaleX = scale, scaleY = scale)
}

/**
 * Fades and slides content up into place when it first appears -- the shared "entrance" motion
 * used across every screen (Home's cards, Progress Tracker's timelines, clinic list items, etc.)
 * so content loading in reads as a deliberate reveal instead of just popping into existence.
 * [delayMillis] staggers a list of these so items cascade in one after another rather than all
 * appearing in lockstep.
 */
@Composable
fun EntranceAnimation(delayMillis: Int = 0, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 5 }
    ) {
        content()
    }
}

/**
 * Shared "not a diagnosis" banner shown at the top of every main screen that displays
 * health-related content. Deliberately bold/high-contrast (not a muted footnote) so it's one
 * of the first things a user notices, not something that blends into the rest of the page.
 */
@Composable
fun DiagnosticAidDisclaimer(modifier: Modifier = Modifier) {
    val settings = LocalAppSettings.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (settings.highContrast) Color(0xFFFFE0B2) else Color(0xFFFFF3E0))
    ) {
        Text(
            "⚕️ DermaLens is a diagnostic aid only. Always consult a dermatologist for professional advice.",
            fontSize = settings.textMd.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF92400E),
            modifier = Modifier.padding(14.dp),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Shared top bar matching the bottom nav's floating glass treatment: a translucent, softly
 * bordered pill instead of a flush, opaque bar. Falls back to a plain solid [TopAppBar] under
 * High Contrast, same rule as the bottom nav -- translucency is inherently low-contrast, so it
 * has no place when that setting is on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DermaGlassTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    titleColor: Color = Color(0xFF111827),
    actions: @Composable RowScope.() -> Unit = {}
) {
    val settings = LocalAppSettings.current

    if (settings.highContrast) {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.Bold, fontSize = settings.textXl.sp) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Go back") }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = titleColor)
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.78f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    val backInteractionSource = remember { MutableInteractionSource() }
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Go back",
                        tint = titleColor,
                        modifier = Modifier
                            .pressScale(backInteractionSource)
                            .padding(4.dp)
                            .clickable(
                                interactionSource = backInteractionSource,
                                indication = null,
                                onClick = onBack
                            )
                            .padding(12.dp)
                    )
                } else {
                    // Matches the back icon's total footprint (24dp icon + 12dp + 4dp padding on
                    // each side = 56dp) so the row's height -- and therefore the pill's height --
                    // stays identical whether or not a screen has a back button.
                    Spacer(modifier = Modifier.size(56.dp))
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = settings.textXl.sp, color = titleColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                actions()
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

object DermaPrefs {
    const val PREFS_NAME = "dermalens_prefs"
    const val KEY_REMEMBER_EMAIL = "remember_email"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    const val KEY_FONT_SIZE = "font_size"
    const val KEY_HIGH_CONTRAST = "high_contrast"
    const val KEY_CONTRIBUTE_DATA = "contribute_data"
    const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    const val KEY_HIDE_SCAN_CONDITIONS_INFO = "hide_scan_conditions_info"
}

/**
 * Set right before navigating a freshly-verified new account to Home, consumed once there to
 * show the Contribute to Research consent prompt. In-memory only (not persisted) since it only
 * needs to survive the single VerifyEmail-to-Home navigation within the same app process.
 */
object NewUserSignal {
    var pendingContributePrompt: Boolean = false
}