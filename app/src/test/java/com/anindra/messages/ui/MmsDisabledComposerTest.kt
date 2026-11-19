package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * With MMS switched off the app sends text only.
 *
 * A voice note, a picture and a contact card are all MMS parts, so the composer
 * must not offer any of them — and the slot the plus held carries the emoji
 * button instead, which is why turning MMS off also turns that setting on.
 */
class MmsDisabledComposerTest {

    private fun source(relative: String): String {
        val app = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
        return File(app, "src/main/java/com/anindra/messages/$relative").readText()
    }

    private val chat: String by lazy { source("ui/ChatScreen.kt") }
    private val main: String by lazy { source("MainActivity.kt") }

    @Test
    fun theSettingExistsAndIsOnByDefault() {
        val store = source("data/SettingsStore.kt")
        assertTrue(store.contains("KEY_MMS_ENABLED"))
        assertTrue(
            "the app sends MMS until the user says otherwise",
            store.contains("""prefs.getBoolean(KEY_MMS_ENABLED, true)""")
        )
    }

    @Test
    fun theComposerLosesAttachAndTheMicrophone() {
        assertTrue(chat.contains("attachEnabled = vm.settings.mmsEnabled"))
        assertTrue(
            "a voice note is an MMS attachment, so the microphone goes with the tray",
            chat.contains("voiceEnabled = hasMicrophone && vm.settings.mmsEnabled")
        )
    }

    @Test
    fun theEmojiButtonTakesThePlusSlot() {
        assertTrue(
            "the button is offered whenever attachments are not",
            chat.contains("showEmojiButton = vm.settings.emojiButtonEnabled ||") &&
                chat.contains("!vm.settings.mmsEnabled")
        )
        assertTrue(
            "and it is drawn in the slot the plus had, not a second one",
            chat.contains("if (!attachEnabled) {") && chat.contains("} else IconButton(onClick = onAttach) {")
        )
        assertTrue(
            "the trailing emoji button must not double up",
            chat.contains("if (showEmojiButton && attachEnabled) {")
        )
    }

    @Test
    fun everySendPathRefusesMedia() {
        val send = main.substringAfter("fun sendMediaAlbum(").substringBefore("fun retryMessage(")
        assertTrue(send.contains("if (!settings.mmsEnabled)"))
        assertTrue(send.contains("R.string.chat_mms_disabled"))
        val retry = main.substringAfter("fun retryMessage(").substringBefore("fun deliveryReportsEnabled(")
        assertTrue("a retry would otherwise put the picture back on the wire",
            retry.contains("!settings.mmsEnabled && msg.mediaUri.isNotBlank()"))
    }

    @Test
    fun aGroupTextFallsBackToSmsInsteadOfMms() {
        assertTrue(
            main.contains("recipients.size > 1 && settings.mmsEnabled &&")
        )
    }

    @Test
    fun theSwitchIsOnTheMmsSupportPage() {
        val screen = source("ui/AdvancedSubScreens.kt")
        assertTrue(screen.contains("fun MmsSupportScreen("))
        assertTrue(screen.contains("R.string.settings_mms_enabled"))
        assertTrue(screen.contains("onCheckedChange = onMmsEnabledChange"))
    }

    @Test
    fun theSwitchAndTheCarriersShareOneCard() {
        // Two cards read as two unrelated things: the switch answers the same
        // question the carrier verdicts do, so they sit in one grouped list
        // split by hairlines.
        val screen = source("ui/AdvancedSubScreens.kt")
        val card = screen.substringAfter("GroupedRowList(")
            .substringBefore("SettingsFooter(")
        assertTrue("the switch is in the card", card.contains("R.string.settings_mms_enabled"))
        assertTrue("and so are the carrier verdicts", card.contains("checks.forEachIndexed"))
        val page = screen.substringAfter("fun MmsSupportScreen(")
            .substringBefore("@Composable\nprivate fun mmsCheckSubtitle")
        assertFalse(
            "a SettingsGroup would draw each row as its own card again",
            page.contains("SettingsGroup {")
        )
    }
}
