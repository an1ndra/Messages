package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Delivery reports are a switch plus a derived label, and the label depends on
 * the switch. Both halves have failed before in ways a compiler cannot
 * see:
 *
 *  - the redesigned Settings screen shipped without the row, so the switch was
 *    unreachable and every message was sent without asking for a confirmation;
 *  - the chat read the switch once and remembered it, so a switch flipped while
 *    a chat was open never reached that chat.
 *
 * Neither is visible in a diff, so both are asserted against the source here.
 */
class DeliveryReportsWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val inboxScreen: String by lazy {
        File(main, "java/com/anindra/messages/ui/InboxSettingsScreen.kt").readText()
    }

    private val settingsScreen: String by lazy {
        File(main, "java/com/anindra/messages/ui/SettingsScreen.kt").readText()
    }

    private val chatScreen: String by lazy {
        File(main, "java/com/anindra/messages/ui/ChatScreen.kt").readText()
    }

    /** The `deliveryReports` state and the row that toggles it. */
    private val deliveryRows: List<String>
        get() = Regex("""SettingsRow\([\s\S]*?settings_delivery_reports_title[\s\S]*?\n\s*\)""")
            .findAll(inboxScreen)
            .map { it.value }
            .toList()

    @Test
    fun inboxSettingsOffersTheSwitch() {
        assertTrue(
            "Inbox settings renders no Delivery reports row",
            deliveryRows.isNotEmpty()
        )
        val row = deliveryRows.first()
        assertTrue("the row shows no subtitle", row.contains("settings_delivery_reports_subtitle"))
        assertTrue("the row does not read the stored value", row.contains("checked = deliveryReports"))
        assertTrue("the row does not write the setting", row.contains("vm.settings.deliveryReportsEnabled = it"))
    }

    @Test
    fun generalSettingsNoLongerOwnsTheSwitch() {
        assertFalse(
            "Delivery reports is still on General settings",
            settingsScreen.contains("settings_delivery_reports_title")
        )
    }

    @Test
    fun theSwitchStartsFromTheStoredValue() {
        assertTrue(
            "Inbox settings' deliveryReports state is not seeded from the setting",
            Regex("""var deliveryReports by remember\(revision\) \{ mutableStateOf\(vm\.settings\.deliveryReportsEnabled\) \}""")
                .containsMatchIn(inboxScreen)
        )
    }

    @Test
    fun theChatReadsTheSwitchThroughTheSettingsRevision() {
        assertTrue(
            "the chat does not watch the settings revision",
            Regex("""val deliveryReports = remember\(settingsRevision\) \{ vm\.deliveryReportsEnabled\(\) \}""")
                .containsMatchIn(chatScreen)
        )
        assertFalse(
            "the chat remembers the switch once and never updates",
            Regex("""deliveryReports = remember \{ vm\.deliveryReportsEnabled\(\) \}""")
                .containsMatchIn(chatScreen)
        )
    }

    @Test
    fun theBubbleLabelHasOneOwner() {
        // The two lists (new and legacy) each drew their own copy of the gate;
        // the second copy is how a delivered row could read "SMS".
        assertTrue(
            "the bubble no longer derives its status word from MessageDetails.meta",
            Regex("""MessageDetails\.meta\(""").findAll(chatScreen).count() >= 2
        )
        assertFalse(
            "a bubble still decides the status word inline",
            Regex("""deliveryReports && msg\.status == "delivered"""").containsMatchIn(chatScreen)
        )
    }
}
