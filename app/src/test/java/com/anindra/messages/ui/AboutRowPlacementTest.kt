package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Where the About row sits, and what it replaced.
 *
 * The row is the last one in Settings so it carries the rounded bottom edge,
 * which means Advanced had to stop being `LAST` — a row that keeps the rounding
 * while another sits under it shows a seam. Both are source facts with no
 * runtime manifestation until somebody looks at the screen, so they are pinned
 * here, and `scripts/test-about.sh` re-checks them from the rendered list.
 */
class AboutRowPlacementTest {

    private val main = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .firstOrNull { it.isDirectory }
        ?: error("app/src/main not found")

    private fun source(path: String) = File(main, "java/com/anindra/messages/$path").readText()

    private val settings = source("ui/SettingsScreen.kt")
    private val legacy = source("ui/legacy/LegacySettingsScreen.kt")
    private val mainActivity = source("MainActivity.kt")
    private val search = source("ui/SettingsSearch.kt")

    @Test
    fun aboutIsTheLastRowAndAdvancedIsNot() {
        val about = Regex(
            """SettingsRow\(\s*position = RowPosition\.(\w+),\s*title = stringResource\(R\.string\.settings_about_title\)"""
        ).find(settings)
        assertTrue("the About row is missing from Settings", about != null)
        assertTrue(
            "About must carry the rounded bottom edge",
            about!!.groupValues[1] == "LAST"
        )

        val advanced = Regex(
            """SettingsRow\(\s*position = RowPosition\.(\w+),\s*title = stringResource\(R\.string\.settings_advanced_title\)"""
        ).find(settings)
        assertTrue("the Advanced row is missing from Settings", advanced != null)
        assertTrue(
            "Advanced sits above About now, so it must not keep the LAST corners",
            advanced!!.groupValues[1] == "MIDDLE"
        )
    }

    @Test
    fun aboutFollowsAdvancedInTheList() {
        val order = Regex("""R\.string\.settings_(advanced_title|about_title)""")
            .findAll(settings)
            .map { it.groupValues[1] }
            .toList()
        assertTrue(
            "About comes after Advanced in the rendered order: $order",
            order.indexOf("about_title") > order.indexOf("advanced_title")
        )
    }

    @Test
    fun theAboutSubtitleCarriesTheBuildVersion() {
        assertTrue(
            "the row reports the version",
            Regex("""R\.string\.settings_about_version,\s*appInfo\.versionName,\s*appInfo\.versionCode""")
                .containsMatchIn(settings)
        )
        // One source for that number, the same one the crash report prints.
        assertTrue(settings.contains("CrashReporter.appInfo(context)"))
        assertTrue(mainActivity.contains("com.anindra.messages.ui.AboutScreen"))
    }

    @Test
    fun theVersionFooterIsGoneFromBothSettingsSurfaces() {
        assertFalse(
            "Settings no longer prints its own footer; the version lives on About",
            settings.contains("SettingsFooter(stringResource(R.string.messages_footer))")
        )
        assertFalse(
            "the legacy sheet no longer prints the same line",
            legacy.contains("stringResource(R.string.messages_footer)")
        )
    }

    @Test
    fun backGoesToSettingsRatherThanClosingTheApp() {
        val screen = source("ui/AboutScreen.kt")
        assertTrue(
            "the screen must take back itself, like the other settings children",
            "BackHandler(onBack = onBack)" in screen
        )
        assertTrue(
            "the root dispatcher must map the route as well",
            Regex(""""about" -> navRoute = "settings"""").containsMatchIn(mainActivity)
        )
    }

    @Test
    fun theListKeepsItsBottomMarginWithoutTheFooter() {
        // The footer supplied the space under the last card; removing it left
        // the About row flush against the bottom inset.
        for ((name, src) in listOf("Settings" to settings, "legacy settings" to legacy)) {
            assertTrue(
                "$name must close the list with room under the last row",
                src.contains("Spacer(Modifier.height(SettingsLayout.BOTTOM_GAP))")
            )
        }
        val about = source("ui/AboutScreen.kt")
        assertTrue(
            "the About screen ends with the same gap",
            about.contains("Spacer(Modifier.height(SettingsLayout.BOTTOM_GAP))")
        )
    }

    @Test
    fun theLegacySheetRendersTheSameRow() {
        assertTrue("legacy row", legacy.contains("R.string.settings_about_title"))
        assertTrue("legacy opens the same route", legacy.contains("onOpenAbout"))
        assertTrue(
            "the legacy row is searchable, or the index test fails",
            search.contains("R.string.settings_about_title")
        )
    }

    @Test
    fun theRouteIsRegisteredForTheNavAnimation() {
        assertTrue(
            "About is a pushed screen, so it animates like one",
            mainActivity.contains("\"about\" to 2")
        )
        assertTrue(
            "the route renders the screen",
            Regex(""""about" -> AboutScreen\(""").containsMatchIn(mainActivity)
        )
        assertTrue(
            "back returns to Settings, where the row lives",
            Regex("""onBack = \{ navRoute = "settings" \},\s*scrollState = aboutScroll""")
                .containsMatchIn(mainActivity)
        )
    }

    @Test
    fun theScreenTakesItsScrollStateInsteadOfCreatingIt() {
        val screen = source("ui/AboutScreen.kt")
        assertTrue("scrollState: ScrollState" in screen)
        assertTrue("verticalScroll(scrollState)" in screen)
        assertFalse(
            "a screen that remembers its own scroll state comes back at the top",
            "verticalScroll(rememberScrollState())" in screen
        )
    }

    @Test
    fun reportingAnIssueNeverClaimsToAttachMessages() {
        // The row's subtitle and the dialog both say what is attached; both
        // must mention that messages are excluded, or the dialog is a surprise.
        val strings = File(main, "res/values/strings_settings.xml").readText()
        assertTrue(
            "the subtitle promises what it does not attach",
            strings.contains("about_issue_subtitle") &&
                Regex("""about_issue_subtitle">[^<]*messages""").containsMatchIn(strings)
        )
        assertTrue(
            "the dialog states the same",
            Regex("""about_diagnostics_body">[^<]*phone number""").containsMatchIn(strings)
        )
    }
}
