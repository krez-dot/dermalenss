package com.dermalens.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun DermaLensTheme(
    // Deliberately not isSystemInDarkTheme() -- every screen in this app hardcodes its own light
    // colors directly (purple headers, white cards, Color(0xFFF8F9FA) backgrounds) rather than
    // reading from MaterialTheme.colorScheme, so there's no actual dark-mode content anywhere to
    // switch to. Following the system setting here just made the *unpainted* edges (the literal
    // system-bar-inset strips a Scaffold's default containerColor shows through) go black on any
    // device with system dark mode on, while every real screen stayed exactly as light as always
    // -- a real, reported bug: black status/navigation bars over an otherwise light-only app.
    darkTheme: Boolean = false,
    // Also off for the same reason -- dynamic color would still resolve a dark variant when the
    // system is in dark mode, reintroducing the same mismatch through a different path.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DermaTypography,
        content = content
    )
}