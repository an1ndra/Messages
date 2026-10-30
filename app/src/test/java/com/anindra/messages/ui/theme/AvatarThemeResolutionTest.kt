package com.anindra.messages.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.anindra.messages.data.SettingsStore
import com.anindra.messages.ui.avatarColor
import com.anindra.messages.ui.avatarInkOn
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
    fun theInkIsChosenFromTheFillItSitsOn() {
        // The night mode is no longer the input: a bright chip needs the dark
        // silhouette in either scheme, which is what GM paints in dark mode and
        // what white-on-pastel was getting wrong in light.
        assertEquals("#202124", hex(avatarInkOn(Color(0xFFFF63B8))))
        assertEquals("#202124", hex(avatarInkOn(Color(0xFF4ECDE6))))
        assertEquals("#FFFFFF", hex(avatarInkOn(Color(0xFF3F51B5))))
    }

    @Test
    fun theAvatarPaintsThroughTheSharedRule() {
        val components = read("src/main/java/com/anindra/messages/ui/Components.kt")
        assertTrue(
            "the avatar must resolve its ink through the shared function",
            components.contains("val glyphTint = tint ?: avatarInkOn(fill)")
        )
        assertTrue(
            "the fill must come from the palette, chosen by contrast, never a theme tint",
            components.contains("val fill = backgroundColor ?: avatarColorOn(key, onSurface)")
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
            diagnostics.contains("\"Avatar: \${data.appDetails.resolvedAvatar}\"")
        )
        assertTrue(
            "the ink must be resolved through the same function the avatar uses",
            diagnostics.contains("com.anindra.messages.ui.avatarColorOn(SAMPLE_AVATAR_KEY, scheme.background)")
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
