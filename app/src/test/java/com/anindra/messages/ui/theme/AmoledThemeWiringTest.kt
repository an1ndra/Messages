package com.anindra.messages.ui.theme

import androidx.compose.ui.graphics.Color
import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AMOLED must be reachable everywhere "dark" is.
 *
 * The mode is a string in three places that are easy to forget: the stored
 * constant, the two `set_theme` intent allowlists the regression scripts drive,
 * and the label each settings screen prints. Missing the intent allowlist is
 * the worst of the three, because the theme then silently refuses to change
 * when a script asks for it and nothing on screen explains why.
 */
class AmoledThemeWiringTest {

    @Test
    fun amoledResolvesAsADarkThemeRegardlessOfTheSystem() {
        // Not following the system is deliberate: picking AMOLED under a light
        // system must still give a black page, not fall through to light.
        assertTrue(themeIsDark(SettingsStore.THEME_AMOLED, systemIsDark = true))
        assertTrue(themeIsDark(SettingsStore.THEME_AMOLED, systemIsDark = false))
    }

    @Test
    fun amoledIsMatchedBeforeTheGeneralDarkBranch() {
        // Order matters: schemeFor falls through to DarkColors on darkTheme, so
        // an AMOLED check placed after it would be unreachable.
        val amoled = schemeFor(SettingsStore.THEME_AMOLED, A11yOptions.DISABLED, darkTheme = true)
        assertEquals(
            "AMOLED must select its own scheme, not fall through to dark",
            AmoledColors.background,
            amoled.background
        )
    }

    @Test
    fun highContrastWinsOverAmoled() {
        // DarkHighContrastColors already sits on #000000, so the page colour is
        // the same and nothing is given up.
        val scheme = schemeFor(
            SettingsStore.THEME_AMOLED,
            A11yOptions(enabled = true, highContrast = true),
            darkTheme = true
        )
        assertEquals(Color(0xFF000000), scheme.background)
        assertEquals(
            "high contrast should win over AMOLED",
            Color(0xFFD6E3FF),
            scheme.primary
        )
    }


    private val app = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app") }
        .firstOrNull { File(it, "src").isDirectory }
        ?: error("app/src not found")

    private fun read(rel: String) = File(app, rel).readText()

    @Test
    fun theStoredConstantExists() {
        val store = read("src/main/java/com/anindra/messages/data/SettingsStore.kt")
        assertTrue(
            "SettingsStore.THEME_AMOLED is missing",
            store.contains("""const val THEME_AMOLED = "amoled"""")
        )
    }

    @Test
    fun bothSetThemeIntentAllowlistsAcceptAmoled() {
        val main = read("src/main/java/com/anindra/messages/MainActivity.kt")
        val allowlists = Regex("\"dark\", \"light\", \"system\"(, \"amoled\")? ->").findAll(main).count()
        assertEquals("expected two set_theme allowlists in MainActivity", 2, allowlists)
        assertTrue(
            "the set_theme allowlists do not accept amoled, so --es set_theme amoled is ignored",
            main.contains(""""dark", "light", "system", "amoled" ->""")
        )
    }

    @Test
    fun bothSettingsScreensOfferAndLabelAmoled() {
        listOf(
            "src/main/java/com/anindra/messages/ui/SettingsScreen.kt",
            "src/main/java/com/anindra/messages/ui/legacy/LegacySettingsScreen.kt"
        ).forEach { path ->
            val src = read(path)
            assertTrue("$path: picker has no amoled entry",
                src.contains(""""amoled" to stringResource(R.string.settings_theme_amoled)"""))
            assertTrue("$path: themeLabel has no amoled branch",
                src.contains(""""amoled" -> context.getString(R.string.settings_theme_amoled)"""))
        }
    }

    @Test
    fun diagnosticsReadsTheSameResolverTheThemeRendersThrough() {
        // Diagnostics re-implementing the branch is how the report would keep
        // saying #000000 after the theme stopped painting it, and the
        // regression script reads the report, so the two must not diverge.
        val theme = read("src/main/java/com/anindra/messages/ui/theme/Theme.kt")
        val report = read("src/main/java/com/anindra/messages/diagnostics/DiagnosticsReport.kt")
        assertTrue(
            "DiagnosticsReport should resolve the scheme through schemeFor()",
            report.contains("schemeFor(mode")
        )
        assertTrue(
            "DiagnosticsReport must not select AmoledColors itself",
            !report.contains("AmoledColors")
        )
        assertTrue(
            "MessagesTheme should select the scheme through schemeFor()",
            theme.contains("schemeFor(mode, a11y, darkTheme)")
        )
    }

    @Test
    fun everyLocaleTranslatesTheLabel() {
        // TranslationParityTest already enforces key parity across locales; this
        // pins the label to a non-empty value, since a blank translation renders
        // as an empty row in the theme picker.
        File(app, "src/main/res").listFiles { f: File ->
            f.isDirectory && (f.name == "values" || f.name.startsWith("values-"))
        }!!.forEach { dir ->
            val file = File(dir, "strings_settings.xml")
            if (!file.isFile) return@forEach
            val m = Regex("""<string name="settings_theme_amoled">(.*?)</string>""")
                .find(file.readText())
            assertTrue("${dir.name}: settings_theme_amoled missing", m != null)
            assertTrue("${dir.name}: settings_theme_amoled is empty", !m!!.groupValues[1].isBlank())
        }
    }
}
