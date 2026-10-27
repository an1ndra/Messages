package com.anindra.messages.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Start chat" pill is an accent surface (#347): the theme's primary fills
 * the pill and onPrimary reads on it, in every seeded theme and under the
 * wallpaper scheme alike, matching Google Messages.
 */
class StartChatPillColorTest {

    private val schemes = mapOf(
        "light" to LightColors,
        "dark" to DarkColors,
        "amoled" to AmoledColors
    )

    @Test
    fun everySeededThemeUsesTheAccentPair() {
        schemes.forEach { (name, scheme) ->
            assertEquals(
                "$name: the pill container must be the accent",
                scheme.primary,
                scheme.startChatContainer
            )
            assertEquals(
                "$name: the pill content must be the matching on-colour",
                scheme.onPrimary,
                scheme.startChatContent
            )
        }
    }

    @Test
    fun highContrastAlsoWalksThroughTheAccentPair() {
        listOf(true, false).forEach { dark ->
            val scheme = schemeFor(
                SettingsStore.THEME_SYSTEM,
                A11yOptions(enabled = true, highContrast = true),
                darkTheme = dark
            )
            assertEquals(scheme.primary, scheme.startChatContainer)
            assertEquals(scheme.onPrimary, scheme.startChatContent)
        }
    }

    @Test
    fun aWallpaperPaletteLetsThePillFollowTheAccent() {
        val wallpaper = lightColorScheme(
            primary = Color(0xFF6E7488),
            onPrimary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFF2A4174),
            onSecondaryContainer = Color(0xFFDCE2F9)
        )
        assertEquals(wallpaper.primary, wallpaper.startChatContainer)
        assertEquals(wallpaper.onPrimary, wallpaper.startChatContent)
        // the previous secondary-container surface is exactly what is left behind
        assertNotEquals(wallpaper.secondaryContainer, wallpaper.startChatContainer)
    }

    @Test
    fun everyResolvedPairStaysReadable() {
        val wallpaper = darkColorScheme(
            primary = Color(0xFF2A4174),
            onPrimary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFF6E7488),
            onSecondaryContainer = Color(0xFF141B2C)
        )
        (schemes.values + wallpaper).forEach { scheme ->
            val ratio = contrastRatio(scheme.startChatContent, scheme.startChatContainer)
            assertTrue(
                "content ${scheme.startChatContent} on container ${scheme.startChatContainer} is $ratio:1",
                ratio >= 3f
            )
        }
    }

    @Test
    fun contrastRatioMatchesTheWcagRange() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 0.01f)
    }
}
