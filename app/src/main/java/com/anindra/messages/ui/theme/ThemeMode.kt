package com.anindra.messages.ui.theme

object ThemeMode {
    const val DYNAMIC_COLOR_MIN_SDK = 31

    /** Material You (wallpaper) colors are available from Android 12. */
    fun useDynamicColor(sdkInt: Int): Boolean = sdkInt >= DYNAMIC_COLOR_MIN_SDK

    /** Resolves the effective dark/light mode from the user preference. */
    fun resolveDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
}
