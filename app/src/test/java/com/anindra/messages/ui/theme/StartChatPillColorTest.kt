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
 * The "Start chat" pill is the tinted accent container (#347), measured off
 * Google Messages on both schemes: #D3E3FD with #0842A0 content in the light
 * theme, #0842A0 with #D3E3FD content in the dark one. A solid primary fill
 * reads as a dead grey slab under a muted wallpaper palette, and
 * secondaryContainer was never the surface GM paints.
 */
class StartChatPillColorTest {

    private val schemes = mapOf(
        "light" to LightColors,
        "dark" to DarkColors,
        "amoled" to AmoledColors
    )

    private fun hex(c: Color) = "#%02X%02X%02X".format(
        (c.red * 255f + 0.5f).toInt(),
        (c.green * 255f + 0.5f).toInt(),
        (c.blue * 255f + 0.5f).toInt()
    )

    @Test
    fun everySeededThemeFillsWithTheAccentContainer() {
        schemes.forEach { (name, scheme) ->
            assertEquals(
                "$name: the pill container must be the accent container",
                scheme.primaryContainer,
                scheme.startChatContainer
            )
        }
    }

    @Test
    fun theLightPillMatchesGoogleMessages() {
        // Measured off Google Messages: a #D3E3FD pill with #0842A0 content.
        assertEquals("#D3E3FD", hex(LightColors.startChatContainer))
        assertEquals("#0B57D0", hex(LightColors.startChatContent))
    }

    @Test
    fun theDarkPillMatchesGoogleMessages() {
        // Measured off Google Messages: a #0842A0 pill with #D3E3FD content.
        assertEquals("#0842A0", hex(DarkColors.startChatContainer))
        assertEquals("#D3E3FD", hex(DarkColors.startChatContent))
    }

    @Test
    fun amoledStaysDarkLikeTheDarkScheme() {
        assertEquals("#0A3D8F", hex(AmoledColors.startChatContainer))
        assertEquals("#D3E3FD", hex(AmoledColors.startChatContent))
    }

    @Test
    fun thePillIsNotTheAccentItself() {
        schemes.forEach { (name, scheme) ->
            assertNotEquals(
                "$name: a solid primary fill goes grey under a wallpaper palette",
                scheme.primary,
                scheme.startChatContainer
            )
        }
    }

    @Test
    fun highContrastAlsoWalksThroughTheContainerPair() {
        listOf(true, false).forEach { dark ->
            val scheme = schemeFor(
                SettingsStore.THEME_SYSTEM,
                A11yOptions(enabled = true, highContrast = true),
                darkTheme = dark
            )
            assertEquals(scheme.primaryContainer, scheme.startChatContainer)
        }
    }

    @Test
    fun aWallpaperPaletteLetsThePillFollowTheContainer() {
        val wallpaper = lightColorScheme(
            primary = Color(0xFF6E7488),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD3E3FD),
            onPrimaryContainer = Color(0xFF041E49)
        )
        assertEquals(wallpaper.primaryContainer, wallpaper.startChatContainer)
    }

    @Test
    fun everyResolvedPairStaysReadable() {
        val wallpaper = darkColorScheme(
            primary = Color(0xFF2A4174),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF2A4174),
            onPrimaryContainer = Color(0xFFDCE2F9)
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
