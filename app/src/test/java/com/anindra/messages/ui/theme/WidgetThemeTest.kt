package com.anindra.messages.ui.theme

import androidx.compose.ui.graphics.Color
import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WidgetThemeTest {

    @Test
    fun fixedDarkIsDarkOnBothSides() {
        val (day, night) = widgetSchemes(SettingsStore.THEME_DARK, A11yOptions.DISABLED)
        assertSame(DarkColors, day)
        assertSame(DarkColors, night)
    }

    @Test
    fun lightIgnoresLauncherNightMode() {
        val (day, night) = widgetSchemes(SettingsStore.THEME_LIGHT, A11yOptions.DISABLED)
        assertSame(LightColors, day)
        assertSame(LightColors, night)
    }

    @Test
    fun amoledStaysAmoledInDaylight() {
        val (day, night) = widgetSchemes(SettingsStore.THEME_AMOLED, A11yOptions.DISABLED)
        assertSame(AmoledColors, day)
        assertSame(AmoledColors, night)
    }

    @Test
    fun systemFollowsTheLauncher() {
        val (day, night) = widgetSchemes(SettingsStore.THEME_SYSTEM, A11yOptions.DISABLED)
        assertSame(LightColors, day)
        assertSame(DarkColors, night)
    }

    @Test
    fun dynamicUsesTheSuppliedSchemes() {
        val dynamicDay = LightColors.copy(primary = Color(0xFFFF0000))
        val dynamicNight = DarkColors.copy(primary = Color(0xFF0000FF))
        val (day, night) = widgetSchemes(
            SettingsStore.THEME_DYNAMIC, A11yOptions.DISABLED, dynamicDay, dynamicNight
        )
        assertEquals(Color(0xFFFF0000), day.primary)
        assertEquals(Color(0xFF0000FF), night.primary)
    }

    @Test
    fun highContrastBeatsAmoled() {
        val a11y = A11yOptions(enabled = true, highContrast = true)
        val (day, night) = widgetSchemes(SettingsStore.THEME_AMOLED, a11y)
        assertSame(DarkHighContrastColors, day)
        assertSame(DarkHighContrastColors, night)
    }
}
