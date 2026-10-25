package com.anindra.messages.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Start chat" pill must look like v1.0.30 again: primary-coloured content
 * on the secondary container. The later Material FAB pair (primaryContainer /
 * onPrimaryContainer) painted the dark pill in a different blue, which is what
 * this pins down.
 */
class StartChatPillColorTest {

    private val schemes = mapOf(
        "light" to LightColors,
        "dark" to DarkColors,
        "amoled" to AmoledColors
    )

    @Test
    fun everySeededThemeUsesTheV130Pair() {
        schemes.forEach { (name, scheme) ->
            assertEquals(
                "$name: the pill container must be the secondary container",
                scheme.secondaryContainer,
                scheme.startChatContainer
            )
            assertEquals(
                "$name: the pill content must be primary, as in v1.0.30",
                scheme.primary,
                scheme.startChatContent
            )
        }
    }

    @Test
    fun highContrastAlsoKeepsTheV130Pair() {
        listOf(true, false).forEach { dark ->
            val scheme = schemeFor(
                SettingsStore.THEME_SYSTEM,
                A11yOptions(enabled = true, highContrast = true),
                darkTheme = dark
            )
            assertEquals(scheme.secondaryContainer, scheme.startChatContainer)
            assertEquals(scheme.primary, scheme.startChatContent)
        }
    }

    @Test
    fun aDimWallpaperPrimaryFallsBackToTheOnContainerRole() {
        // The AVD's wallpaper palette hands back exactly this kind of pair:
        // primary #6E7488 on secondaryContainer #2A4174 is 2.14:1, a blob.
        val wallpaper = lightColorScheme(
            primary = Color(0xFF6E7488),
            onPrimary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFF2A4174),
            onSecondaryContainer = Color(0xFFDCE2F9)
        )
        assertEquals(wallpaper.secondaryContainer, wallpaper.startChatContainer)
        assertEquals(wallpaper.onSecondaryContainer, wallpaper.startChatContent)
        assertTrue(
            "the fallback pair must stay readable",
            contrastRatio(wallpaper.startChatContent, wallpaper.startChatContainer) >= 3f
        )
    }

    @Test
    fun everyResolvedPairStaysReadable() {
        val wallpaper = darkColorScheme(
            primary = Color(0xFF2A4174),
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
