package com.anindra.messages.diagnostics

import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The colour Diagnostics reports must be the colour that actually renders.
 *
 * The sheet is what the AMOLED regression script reads, so if this drifts from
 * the scheme the script would assert against a value the app never paints. The
 * theme resolves in Theme.kt; these cases pin the same branch order here.
 */
class ResolvedBackgroundTest {

    @Test
    fun amoledIsPureBlackRegardlessOfTheSystemSetting() {
        // Not following the system is the point: choosing AMOLED under a light
        // system must still give a black page.
        assertEquals("#000000", resolvedBackgroundHex(SettingsStore.THEME_AMOLED, systemIsDark = true))
        assertEquals("#000000", resolvedBackgroundHex(SettingsStore.THEME_AMOLED, systemIsDark = false))
    }

    @Test
    fun darkIsDarkGreyInEitherSystemSetting() {
        assertEquals("#131314", resolvedBackgroundHex(SettingsStore.THEME_DARK, systemIsDark = true))
        assertEquals("#131314", resolvedBackgroundHex(SettingsStore.THEME_DARK, systemIsDark = false))
    }

    @Test
    fun lightIsLightInEitherSystemSetting() {
        assertEquals("#F8F9FC", resolvedBackgroundHex(SettingsStore.THEME_LIGHT, systemIsDark = true))
        assertEquals("#F8F9FC", resolvedBackgroundHex(SettingsStore.THEME_LIGHT, systemIsDark = false))
    }

    @Test
    fun systemFollowsTheDevice() {
        assertEquals("#131314", resolvedBackgroundHex(SettingsStore.THEME_SYSTEM, systemIsDark = true))
        assertEquals("#F8F9FC", resolvedBackgroundHex(SettingsStore.THEME_SYSTEM, systemIsDark = false))
    }

    @Test
    fun amoledAndDarkAreDistinguishable() {
        // The whole regression: if these were equal, picking AMOLED would do
        // nothing and the script's colour assertion would be meaningless.
        assertNotEquals(
            "AMOLED must not resolve to the dark scheme's page colour",
            resolvedBackgroundHex(SettingsStore.THEME_DARK, systemIsDark = true),
            resolvedBackgroundHex(SettingsStore.THEME_AMOLED, systemIsDark = true)
        )
    }

    @Test
    fun anUnknownModeFallsBackToFollowingTheSystem() {
        assertEquals("#131314", resolvedBackgroundHex("nonsense", systemIsDark = true))
        assertEquals("#F8F9FC", resolvedBackgroundHex("nonsense", systemIsDark = false))
    }

    @Test
    fun theHexIsSixUppercaseDigits() {
        // The report is grepped by the regression script, so the format is part
        // of the contract rather than incidental.
        listOf("system", "light", "dark", "amoled").forEach { mode ->
            val hex = resolvedBackgroundHex(mode, true)
            assertEquals("$mode -> $hex is not #RRGGBB", 7, hex.length)
            assert(hex.startsWith("#")) { "$mode -> $hex has no leading #" }
            assert(hex.drop(1).all { it in '0'..'9' || it in 'A'..'F' }) {
                "$mode -> $hex is not uppercase hex"
            }
        }
    }
}
