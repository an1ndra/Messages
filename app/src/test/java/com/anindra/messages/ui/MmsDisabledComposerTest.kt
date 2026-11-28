package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * With MMS switched off the app sends text only.
 *
 * A voice note, a picture and a contact card are all MMS parts, so the composer
 * must not offer any of them. Turning MMS off turns the emoji setting on so the
 * button fills the empty slot; after that the setting alone hides the button.
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
    fun theEmojiSettingAloneDecidesTheButton() {
        assertTrue(
            "the composer reads only the setting after the switch has done its job",
            chat.contains("showEmojiButton = vm.settings.emojiButtonEnabled,")
        )
        assertFalse(
            "there is no MMS override left in the composer",
            chat.contains("showEmojiButton = vm.settings.emojiButtonEnabled ||")
        )
        assertTrue(
            "it is drawn in the slot the plus had, not a second one",
            chat.contains("if (!attachEnabled) {") && chat.contains("} else IconButton(onClick = onAttach) {")
        )
        assertTrue(
            "the trailing emoji button must not double up",
            chat.contains("if (showEmojiButton && attachEnabled) {")
        )
    }

    @Test
    fun turningMmsOffTurnsTheEmojiSettingOnButItCanBeDisabledAfter() {
        assertTrue(
            "one helper owns the turn-off behaviour",
            main.contains("fun setMmsEnabled(enabled: Boolean)")
        )
        assertTrue(
            "the emoji button fills the slot the plus left",
            main.contains("if (!enabled) settings.emojiButtonEnabled = true")
        )
        assertTrue(
            "the settings switch uses that helper",
            main.contains("onMmsEnabledChange = { on -> vm.setMmsEnabled(on) }")
        )
        assertTrue(
            "the debug probe uses the same helper",
            main.contains("if (probe == \"off\") vm.setMmsEnabled(false)")
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
        assertTrue(screen.contains("onChecked = onMmsEnabledChange"))
    }

    @Test
    fun theRowsMatchTheAdvancedSettingsCard() {
        val screen = source("ui/AdvancedSubScreens.kt")
        val page = screen.substringAfter("fun MmsSupportScreen(")
            .substringBefore("@Composable\nprivate fun mmsCheckSubtitle")
        assertTrue(
            "rows use the same group as Advanced settings, reachable from search",
            page.contains("SearchableSettingsCard(0) {")
        )
        assertTrue("the switch is a SettingsRow", page.contains("SettingsRow("))
        assertTrue("the switch opens the group", page.contains("RowPosition.FIRST"))
        assertTrue("the carrier verdicts follow", page.contains("checks.forEachIndexed"))
        assertTrue("the last verdict closes the group", page.contains("RowPosition.LAST"))
        assertFalse("no custom row styling", page.contains("MmsCardRow("))
        assertFalse("no separate hairline card", page.contains("GroupedRowList("))
    }

    @Test
    fun theFooterSaysTheCheckIsNotAGuaranteeAndAlignsWithTheRows() {
        val screen = source("ui/AdvancedSubScreens.kt")
        assertFalse("the count summary is gone", screen.contains("mms_check_summary"))
        assertTrue(
            "the note starts where the carrier text starts",
            screen.contains("horizontalPadding = SettingsLayout.ROW_CONTENT_PADDING")
        )
        val app = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
        val strings = File(app, "src/main/res/values/strings_advanced.xml").readText()
        val footer = Regex("""<string name="mms_check_footer">([^<]*)</string>""")
            .find(strings)?.groupValues?.get(1) ?: error("mms_check_footer missing")
        assertTrue("still says it is only a check", footer.contains("does not confirm"))
        assertTrue("says why it cannot confirm", footer.contains("cannot test"))
        assertTrue("says plainly it is not guaranteed", footer.contains("never guaranteed"))
        assertTrue("the old carrier/APN essay is gone", footer.length < 250)
    }
}
