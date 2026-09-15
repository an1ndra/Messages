package com.anindra.messages.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun dynamicColorOnlyOnAndroid12AndUp() {
        assertFalse(ThemeMode.useDynamicColor(29))
        assertFalse(ThemeMode.useDynamicColor(30))
        assertTrue(ThemeMode.useDynamicColor(31))
        assertTrue(ThemeMode.useDynamicColor(35))
    }

    @Test
    fun explicitModeOverridesSystem() {
        assertTrue(ThemeMode.resolveDark("dark", systemDark = false))
        assertFalse(ThemeMode.resolveDark("light", systemDark = true))
    }

    @Test
    fun systemModeFollowsSystem() {
        assertTrue(ThemeMode.resolveDark("system", systemDark = true))
        assertFalse(ThemeMode.resolveDark("system", systemDark = false))
        assertTrue(ThemeMode.resolveDark("anything-else", systemDark = true))
    }
}
