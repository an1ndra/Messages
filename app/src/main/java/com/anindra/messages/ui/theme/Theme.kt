package com.anindra.messages.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView

/**
 * Material 3 color system (m3.material.io/styles/color/system).
 *
 * Roles are generated from the seed color Google Blue (#0B57D0) into tonal
 * palettes, then mapped to the standard M3 scheme roles for light & dark.
 * UI code must reference roles via MaterialTheme.colorScheme — never hardcode.
 */

// ────────────────────────── Light scheme (seed #0B57D0) ─────────────────────────
private val LightColors = lightColorScheme(
    // Primary
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    inversePrimary = Color(0xFFA8C7FA),
    // Secondary
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    // Tertiary
    tertiary = Color(0xFF37618E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD2E4FF),
    onTertiaryContainer = Color(0xFF001C38),
    // Error
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    // Surface & background
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF444746),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF0F0F7),
    // M3 surface container roles (tonal elevation ladder)
    surfaceDim = Color(0xFFD8DAE0),
    surfaceBright = Color(0xFFFCFCFE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFECECF3),
    surfaceContainerHigh = Color(0xFFE6E7ED),
    surfaceContainerHighest = Color(0xFFE1E2E8)
)

// ────────────────────────── Dark scheme (seed #A8C7FA) ──────────────────────────
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    inversePrimary = Color(0xFF0B57D0),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFA2CAFF),
    onTertiary = Color(0xFF00315B),
    tertiaryContainer = Color(0xFF1D4975),
    onTertiaryContainer = Color(0xFFD2E4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131314),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF131314),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C7C5),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF2E3036),
    surfaceDim = Color(0xFF131314),
    surfaceBright = Color(0xFF39393C),
    surfaceContainerLowest = Color(0xFF0E0E11),
    surfaceContainerLow = Color(0xFF1B1B1D),
    surfaceContainer = Color(0xFF1E1F20),
    surfaceContainerHigh = Color(0xFF28292C),
    surfaceContainerHighest = Color(0xFF333537)
)

// Semantic aliases so screens express intent, not raw colors.
val ColorScheme.outgoingBubble: Color get() = primaryContainer          // GM blue bubble
val ColorScheme.incomingBubble: Color get() = surfaceContainerHighest   // GM grey bubble
val ColorScheme.chatBar: Color get() = surfaceContainerLow              // input / top bars
val ColorScheme.inputPill: Color get() = surfaceContainerHigh           // text field pill

@Composable
fun MessagesTheme(
    mode: String = "system",   // system | light | dark
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    // Match status-bar icon appearance to the *app* theme (not just the system).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
