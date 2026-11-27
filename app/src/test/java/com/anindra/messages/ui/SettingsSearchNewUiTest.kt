package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsSearchNewUiTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun src(name: String) = File(main, name).readText()

    @Test
    fun theCatalogCoversEveryRedesignedSettingsSurface() {
        val routes = SettingsSearch.NEW_OPTIONS.map { it.route }.toSet()
        val expected = setOf(
            SettingsSearch.Route.SETTINGS,
            SettingsSearch.Route.INBOX,
            SettingsSearch.Route.ADVANCED,
            SettingsSearch.Route.NOTIFICATIONS,
            SettingsSearch.Route.AUTO_DELETE,
            SettingsSearch.Route.LINKS,
            SettingsSearch.Route.ACCESSIBILITY,
            SettingsSearch.Route.MMS,
            SettingsSearch.Route.ABOUT
        )
        assertEquals("a settings screen has no searchable rows", expected, routes)
    }

    @Test
    fun aRepeatedLabelIsKeptSeparatePerScreen() {
        val copies = SettingsSearch.NEW_OPTIONS.filter {
            it.rowRes == com.anindra.messages.R.string.settings_scheduled_title
        }
        assertEquals(2, copies.size)
        assertEquals(
            setOf(SettingsSearch.Route.SETTINGS, SettingsSearch.Route.ADVANCED),
            copies.map { it.route }.toSet()
        )
    }

    @Test
    fun aResultCanNameTheScreenItWillOpen() {
        assertTrue(SettingsSearch.NEW_OPTIONS.all { it.screenRes != 0 })
    }

    @Test
    fun advancedSettingsOwnsTheSearchUi() {
        val advanced = src("java/com/anindra/messages/ui/AdvancedSettingsScreen.kt")
        assertTrue("no search icon", advanced.contains("Icons.Outlined.Search"))
        assertTrue("no query field", advanced.contains("R.string.settings_search_hint"))
        assertTrue("no empty state", advanced.contains("R.string.settings_search_none"))
        assertTrue(
            "picking a result does not navigate",
            advanced.contains("onSearchNavigate(option.route, option.rowRes)")
        )
    }

    @Test
    fun theJumpedRowIsReportedAsTextForTheDeviceScript() {
        val jump = src("java/com/anindra/messages/ui/SettingsSearchJump.kt")
        assertTrue(
            "a jump has no text proof",
            jump.contains("Log.d(\"SettingsSearch\", \"jumped row=${'$'}{target.rowRes}")
        )
        assertTrue(
            "rows cannot report their scroll offset",
            jump.contains("fun SearchableSettingsCard(")
        )
    }
}
