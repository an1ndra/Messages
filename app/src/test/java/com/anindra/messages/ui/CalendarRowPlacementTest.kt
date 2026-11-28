package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CalendarRowPlacementTest {

    private val ui: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/anindra/messages/ui") }
            .firstOrNull { it.isDirectory }
            ?: error("ui source not found")
    }

    private fun src(name: String) = File(ui, name).readText()

    @Test
    fun advancedSettingsNoLongerOwnsTheCalendarRow() {
        val advanced = src("AdvancedSettingsScreen.kt")
        assertFalse(
            "Calendar still sits in Advanced settings",
            advanced.contains("R.string.settings_calendar_title")
        )
        assertFalse("Advanced still owns the picker", advanced.contains("CalendarPickerDialog("))
    }

    @Test
    fun inboxSettingsOwnsTheCalendarRowAndThePicker() {
        val inbox = src("InboxSettingsScreen.kt")
        assertTrue(
            "Inbox settings has no Calendar row",
            inbox.contains("R.string.settings_calendar_title")
        )
        assertTrue("Inbox settings has no picker", inbox.contains("CalendarPickerDialog("))
    }

    @Test
    fun bothScreensReadTheSameStoredChoice() {
        val advanced = src("AdvancedSettingsScreen.kt")
        val inbox = src("InboxSettingsScreen.kt")
        assertFalse(advanced.contains("calendarChoice"))
        assertTrue(inbox.contains("vm.settings.calendarChoice"))
        assertTrue(inbox.contains("vm.settings.calendarChoice = it"))
    }
}
