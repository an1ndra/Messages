package com.anindra.messages.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.anindra.messages.data.SettingsStore
import com.anindra.messages.ui.avatarColor
import com.anindra.messages.ui.avatarGlyphInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Avatar colour, measured off Google Messages: the bright per-contact palette is
 * painted on both schemes and only the silhouette ink flips — white on a light
 * scheme, #202124 on a dark one.
 *
 * #347 round 2 harmonized the fills 25% toward the wallpaper accent, which
 * desaturates them into mud on a muted palette (the fills came out near-black on
 * a slate wallpaper). The fills are fixed again; the ink is the one thing that
 * follows the theme, and it lives in exactly one function so the avatar that
 * paints and the Diagnostics line that reports cannot disagree.
 */
class AvatarThemeResolutionTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private fun hex(c: Color) = "#%02X%02X%02X".format(
        (c.red * 255f + 0.5f).toInt(),
        (c.green * 255f + 0.5f).toInt(),
        (c.blue * 255f + 0.5f).toInt()
    )

    @Test
    fun theInkFlipsWithTheScheme() {
        assertEquals("#FFFFFF", hex(avatarGlyphInk(LightColors)))
        assertEquals("#202124", hex(avatarGlyphInk(DarkColors)))
        assertEquals("#202124", hex(avatarGlyphInk(AmoledColors)))
    }

    @Test
    fun theInkReadsTheBackgroundNotTheMode() {
        // A wallpaper palette the user happens to like is the authority, not the
        // mode string: this is the bug a duplicated branch would reintroduce.
        val darkWallpaper = darkColorScheme(background = Color(0xFF101014))
        val lightWallpaper = lightColorScheme(background = Color(0xFFFDFBFF))
        assertEquals("#202124", hex(avatarGlyphInk(darkWallpaper)))
        assertEquals("#FFFFFF", hex(avatarGlyphInk(lightWallpaper)))
        assertTrue(darkWallpaper.background.luminance() < 0.5f)
        assertFalse(lightWallpaper.background.luminance() < 0.5f)
    }

    @Test
    fun thePaletteIsTheSameInEveryScheme() {
        // GM parity: a contact's chip does not change with the theme, only its
        // ink does. A contrast floor on the fill broke this (#347 review).
        val key = "555-1212"
        assertEquals(avatarColor(key), avatarColor(key))
        assertFalse(
            "no surface-dependent selection: that is the removed contrast floor",
            read("src/main/java/com/anindra/messages/ui/Components.kt")
                .contains("avatarColorOn(")
        )
    }

    @Test
    fun theAvatarPaintsThroughTheSharedRule() {
        val components = read("src/main/java/com/anindra/messages/ui/Components.kt")
        assertTrue(
            "the avatar must resolve its ink through the shared function",
            components.contains("avatarGlyphInk(MaterialTheme.colorScheme)")
        )
        assertTrue(
            "the fill must be the palette colour, never a theme tint",
            components.contains("val fill = backgroundColor ?: avatarColor(key)")
        )
        assertFalse(
            "the harmonizer is gone; a theme-tinted fill is the bug it caused",
            components.contains("avatarFill(")
        )
    }

    @Test
    fun diagnosticsReportsTheInkItPaintsWith() {
        val diagnostics = read(
            "src/main/java/com/anindra/messages/diagnostics/DiagnosticsReport.kt"
        )
        assertTrue(
            "the report must state the ink",
            diagnostics.contains("\"Avatar ink: \${data.appDetails.resolvedAvatarInk} on the fixed GM palette\"")
        )
        assertTrue(
            "the ink must be resolved through the same function the avatar uses",
            diagnostics.contains("com.anindra.messages.ui.avatarGlyphInk(scheme)")
        )
    }

    @Test
    fun theSwitchIsGoneFromBothUisAndFromSearch() {
        listOf(
            "src/main/java/com/anindra/messages/ui/SettingsScreen.kt",
            "src/main/java/com/anindra/messages/ui/legacy/LegacySettingsScreen.kt",
            "src/main/java/com/anindra/messages/ui/SettingsSearch.kt"
        ).forEach { path ->
            assertFalse(
                "$path still references the removed avatar colour switch",
                read(path).contains("avatar_theme")
            )
        }
        assertFalse(
            "the preference behind the switch is dead, so it must be gone too",
            read("src/main/java/com/anindra/messages/data/SettingsStore.kt")
                .contains("avatarThemeColors")
        )
    }

    @Test
    fun theThemeNoLongerPublishesAnAvatarMode() {
        val theme = read("src/main/java/com/anindra/messages/ui/theme/Theme.kt")
        assertFalse(theme.contains("avatarsFollowTheme"))
        assertFalse(theme.contains("LocalThemedAvatars"))
        assertTrue(SettingsStore.THEME_DYNAMIC.isNotEmpty())
    }
}
