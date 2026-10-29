package com.anindra.messages.ui

import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Scheduling is reached by holding Send — the behaviour before #386, restored on
 * request. There is deliberately no clock button beside it: the composer's row
 * keeps only Send, as Google Messages does.
 *
 * The setting still gates the gesture, so it is not the dead control #386 found
 * (it was written by the legacy screen and read nowhere). Hold-Send opens the
 * picker only while scheduling is enabled.
 */
class ScheduleLongPressTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val chat get() = read("src/main/java/com/anindra/messages/ui/ChatScreen.kt")

    @Test
    fun holdingSendOpensTheSchedulePicker() {
        assertTrue(
            "hold-Send must stay wired to the schedule picker",
            Regex("""onLongClick = if \(schedulingEnabled\) onSchedule else null""")
                .containsMatchIn(chat)
        )
    }

    @Test
    fun theComposerHasNoScheduleButton() {
        assertFalse(
            "no clock icon in the composer row",
            chat.contains("Icons.Rounded.Schedule")
        )
        assertFalse(
            "and no schedule affordance of its own",
            chat.contains("contentDescription = scheduleLabel")
        )
    }

    @Test
    fun theSettingStillGatesTheGestureAndIsReachable() {
        assertTrue(
            "the chat passes the stored preference",
            chat.contains("schedulingEnabled = vm.settings.scheduledMessagesEnabled")
        )
        assertTrue(
            "and the new settings UI can change it",
            read("src/main/java/com/anindra/messages/ui/AdvancedSettingsScreen.kt")
                .contains("vm.settings.scheduledMessagesEnabled = it")
        )
        assertTrue(
            "the preference defaults to on",
            read("src/main/java/com/anindra/messages/data/SettingsStore.kt")
                .contains("prefs.getBoolean(KEY_SCHEDULED_MESSAGES_ENABLED, true)")
        )
    }
}
