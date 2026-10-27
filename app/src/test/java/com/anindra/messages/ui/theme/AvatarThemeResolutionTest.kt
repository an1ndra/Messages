package com.anindra.messages.ui.theme

import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Avatar colour: the fixed per-contact palette everywhere, the theme's container
 * colour under the dynamic (wallpaper) theme.
 *
 * The dynamic theme is the one case where the palette would be the only thing on
 * screen not derived from the wallpaper, so it wins there — and because that
 * makes the old "Theme colours for avatars" switch either dead (dynamic) or
 * marginal (every other mode), the switch is gone. Losing the setting must not
 * lose the rule, and the rule must stay in one place: the theme paints through
 * `avatarsFollowTheme`, Diagnostics reports through it.
 */
class AvatarThemeResolutionTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val fixedModes get() = listOf(
        SettingsStore.THEME_LIGHT,
        SettingsStore.THEME_DARK,
        SettingsStore.THEME_AMOLED,
        SettingsStore.THEME_SYSTEM
    )

    @Test
    fun dynamicFollowsTheTheme() {
        assertTrue(avatarsFollowTheme(SettingsStore.THEME_DYNAMIC))
    }

    @Test
    fun everyFixedModeKeepsThePalette() {
        fixedModes.forEach { mode ->
            assertFalse("$mode must keep the per-contact palette", avatarsFollowTheme(mode))
        }
    }

    @Test
    fun theThemePaintsThroughTheSharedRule() {
        assertTrue(
            "MessagesTheme must resolve the mode through avatarsFollowTheme",
            read("src/main/java/com/anindra/messages/ui/theme/Theme.kt")
                .contains("LocalThemedAvatars provides avatarsFollowTheme(mode)")
        )
        assertTrue(
            "the avatar must read the published value",
            read("src/main/java/com/anindra/messages/ui/Components.kt")
                .contains("LocalThemedAvatars.current")
        )
    }

    @Test
    fun diagnosticsStatesOneRuleForEveryTheme() {
        val diagnostics = read(
            "src/main/java/com/anindra/messages/diagnostics/DiagnosticsReport.kt"
        )
        assertTrue(
            "the report must state the rule",
            diagnostics.contains(
                "\"Avatar fill: harmonized per-contact colour under Dynamic, palette otherwise\""
            )
        )
        assertTrue(
            "the line must not branch on the theme, or two identical builds would " +
                "report different avatar text",
            !diagnostics.contains("avatarsFollowTheme(")
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
}
