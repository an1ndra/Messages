package com.anindra.messages.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Accessibility mode options. [enabled] is the master gate: while it is false
 * every derived flag is a no-op so the app renders exactly as it does without
 * accessibility mode. Individual options only take effect once enabled.
 */
data class A11yOptions(
    val enabled: Boolean = false,
    val fontScalePercent: Int = DEFAULT_PERCENT,
    val bold: Boolean = false,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val largeTouchTargets: Boolean = false
) {
    /** App text multiplier applied on top of the system font scale. */
    val fontScale: Float
        get() = if (!enabled) 1f else
            fontScalePercent.coerceIn(MIN_PERCENT, MAX_PERCENT) / 100f

    val boldEnabled: Boolean get() = enabled && bold
    val highContrastEnabled: Boolean get() = enabled && highContrast
    val reduceMotionEnabled: Boolean get() = enabled && reduceMotion
    val largeTouchTargetsEnabled: Boolean get() = enabled && largeTouchTargets

    companion object {
        const val MIN_PERCENT = 85
        const val MAX_PERCENT = 130
        const val DEFAULT_PERCENT = 100
        val PERCENT_OPTIONS = listOf(85, 100, 115, 130)
        val DISABLED = A11yOptions()
    }
}

val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalLargeTouchTargets = staticCompositionLocalOf { false }
