package com.anindra.messages.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #347: the wallpaper-derived ("Material You") scheme must be reachable wherever
 * the seeded palettes are, and it must go through the same resolver the theme
 * and Diagnostics share.
 */
class DynamicThemeWiringTest {

    private val app = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app") }
        .firstOrNull { File(it, "src").isDirectory }
        ?: error("app/src not found")

    private fun read(rel: String) = File(app, rel).readText()

    private val wallpaper: ColorScheme = darkColorScheme(background = Color(0xFF102030))

    @Test
    fun dynamicFollowsTheSystemRatherThanPinningANightMode() {
        assertTrue(themeIsDark(SettingsStore.THEME_DYNAMIC, systemIsDark = true))
        assertFalse(themeIsDark(SettingsStore.THEME_DYNAMIC, systemIsDark = false))
    }

    @Test
    fun theSuppliedWallpaperSchemeIsWhatGetsPainted() {
        assertEquals(
            wallpaper.background,
            schemeFor(
                SettingsStore.THEME_DYNAMIC, A11yOptions.DISABLED, darkTheme = true, dynamic = wallpaper
            ).background
        )
    }

    @Test
    fun withoutAWallpaperSchemeItFallsBackToTheSeededPalettes() {
        // API < 31 has no wallpaper colours at all; the mode must still render.
        assertEquals(
            DarkColors.background,
            schemeFor(SettingsStore.THEME_DYNAMIC, A11yOptions.DISABLED, darkTheme = true, dynamic = null)
                .background
        )
        assertEquals(
            LightColors.background,
            schemeFor(SettingsStore.THEME_DYNAMIC, A11yOptions.DISABLED, darkTheme = false, dynamic = null)
                .background
        )
    }

    @Test
    fun highContrastAndAmoledOutrankTheWallpaper() {
        assertEquals(
            Color(0xFF000000),
            schemeFor(
                SettingsStore.THEME_DYNAMIC,
                A11yOptions(enabled = true, highContrast = true),
                darkTheme = true,
                dynamic = wallpaper
            ).background
        )
        assertEquals(
            AmoledColors.background,
            schemeFor(
                SettingsStore.THEME_AMOLED, A11yOptions.DISABLED, darkTheme = true, dynamic = wallpaper
            ).background
        )
    }

    @Test
    fun theWallpaperIsIgnoredForEveryOtherMode() {
        val pinned = lightColorScheme(background = Color(0xFF00FF00))
        assertEquals(
            LightColors.background,
            schemeFor(
                SettingsStore.THEME_LIGHT, A11yOptions.DISABLED, darkTheme = false, dynamic = pinned
            ).background
        )
    }

    @Test
    fun theStoredConstantExists() {
        assertTrue(
            read("src/main/java/com/anindra/messages/data/SettingsStore.kt")
                .contains("""const val THEME_DYNAMIC = "dynamic"""")
        )
    }

    @Test
    fun bothSetThemeIntentAllowlistsAcceptDynamic() {
        // The regression scripts drive the theme through these allowlists; an
        // entry missing here makes --es set_theme dynamic a silent no-op.
        val main = read("src/main/java/com/anindra/messages/MainActivity.kt")
        assertEquals(
            2,
            Regex("""\"dynamic\" ->""").findAll(main).count()
        )
    }

    @Test
    fun bothSettingsScreensOfferAndLabelDynamic() {
        listOf(
            "src/main/java/com/anindra/messages/ui/SettingsScreen.kt",
            "src/main/java/com/anindra/messages/ui/legacy/LegacySettingsScreen.kt"
        ).forEach { path ->
            val src = read(path)
            assertTrue(
                "$path: picker has no dynamic entry",
                src.contains("stringResource(R.string.settings_theme_dynamic)")
            )
            assertTrue(
                "$path: themeLabel has no dynamic branch",
                src.contains(""""dynamic" -> context.getString(R.string.settings_theme_dynamic)""")
            )
        }
    }

    @Test
    fun theThemeRendersThroughOneResolverAndDiagnosticsAgrees() {
        val theme = read("src/main/java/com/anindra/messages/ui/theme/Theme.kt")
        assertTrue(
            "MessagesTheme must hand the wallpaper scheme to schemeFor()",
            theme.contains("dynamicSchemeOrNull(")
        )
        assertTrue(
            "only one place may turn a mode into a scheme",
            Regex("""mode == SettingsStore\.THEME_DYNAMIC && dynamic != null -> dynamic""")
                .containsMatchIn(theme)
        )
    }

    @Test
    fun everyLocaleTranslatesTheLabel() {
        File(app, "src/main/res").listFiles { f: File ->
            f.isDirectory && (f.name == "values" || f.name.startsWith("values-"))
        }!!.forEach { dir ->
            val file = File(dir, "strings_settings.xml")
            if (!file.isFile) return@forEach
            val value = Regex("""<string name="settings_theme_dynamic">([^<]*)</string>""")
                .find(file.readText())?.groupValues?.get(1)
            assertTrue("${dir.name} has no settings_theme_dynamic", value != null)
            assertTrue("${dir.name} ships a blank settings_theme_dynamic", !value.isNullOrBlank())
        }
    }
}
