package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegacyMmsSupportRowTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val legacyScreen: String by lazy {
        File(main, "java/com/anindra/messages/ui/legacy/LegacyAdvancedSettingsScreen.kt").readText()
    }

    private val settingsSearch: String by lazy {
        File(main, "java/com/anindra/messages/ui/SettingsSearch.kt").readText()
    }

    private val mainActivity: String by lazy {
        File(main, "java/com/anindra/messages/MainActivity.kt").readText()
    }

    @Test
    fun theLegacyAdvancedScreenHasAnMmsSupportRow() {
        val card14Start = legacyScreen.indexOf("SettingsCard(14) {")
        assertTrue("card 14 (Support) not found in the legacy screen", card14Start > 0)
        val card14End = legacyScreen.indexOf("Spacer(Modifier.height(8.dp))", card14Start)
        assertTrue("could not find the end of card 14", card14End > card14Start)
        val card14 = legacyScreen.substring(card14Start, card14End)

        assertTrue(
            "legacy UI: mms_check_title row is missing from the Support card",
            card14.contains("R.string.mms_check_title")
        )
        assertTrue(
            "legacy UI: mms_check_subtitle row is missing from the Support card",
            card14.contains("R.string.mms_check_subtitle")
        )
    }

    @Test
    fun theLegacyScreenWiresOnOpenMmsCheck() {
        assertTrue(
            "legacy UI: AdvancedSettingsScreen must accept onOpenMmsCheck parameter",
            legacyScreen.contains("onOpenMmsCheck: () -> Unit = {}")
        )
        assertTrue(
            "legacy UI: the MMS support row must call onOpenMmsCheck",
            legacyScreen.contains("onClick = onOpenMmsCheck")
        )
    }

    @Test
    fun mainActivityPassesTheMmsCheckRouteToTheLegacyScreen() {
        val callStart = mainActivity.indexOf("legacy.AdvancedSettingsScreen(")
        assertTrue("legacy AdvancedSettingsScreen call not found in MainActivity", callStart > 0)
        val callEnd = mainActivity.indexOf(")", callStart)
        assertTrue("could not find the end of the legacy AdvancedSettingsScreen call", callEnd > callStart)
        val call = mainActivity.substring(callStart, callEnd)

        assertTrue(
            "MainActivity must pass onOpenMmsCheck to the legacy AdvancedSettingsScreen",
            call.contains("onOpenMmsCheck")
        )
        assertTrue(
            "MainActivity must navigate to mms-check route from the legacy screen",
            call.contains("\"mms-check\"")
        )
    }

    @Test
    fun mmsCheckIsSearchableFromTheLegacySettingsSearch() {
        val card14Start = settingsSearch.indexOf("card(Route.ADVANCED, 14,")
        assertTrue("card 14 not found in SettingsSearch", card14Start > 0)
        val card14End = settingsSearch.indexOf(")", card14Start)
        assertTrue("could not find the end of card 14 in SettingsSearch", card14End > card14Start)
        val card14 = settingsSearch.substring(card14Start, card14End)

        assertTrue(
            "SettingsSearch: mms_check_title must be in the legacy search index",
            card14.contains("R.string.mms_check_title")
        )
    }
}
