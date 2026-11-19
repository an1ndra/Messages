package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tray the attach button opens is the tile grid that sits in the keyboard's
 * slot, not the modal sheet it used to open.
 *
 * Merging the branch that built the tray kept the older `AttachSheet` wired into
 * the chat, so `AttachTray` compiled and shipped unused: the screen still dimmed
 * the conversation behind a sheet and left the keyboard up underneath it. These
 * assertions hold the chat to the tray, in the chat's own bottom bar.
 */
class AttachTrayWiringTest {

    private val chat: String by lazy {
        val app = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
        File(app, "src/main/java/com/anindra/messages/ui/ChatScreen.kt").readText()
    }

    /** The chat's bottom bar: the composer and, under it, the tray. */
    private val bottomBar: String
        get() = chat.substringAfter("bottomBar = {").substringBefore(") { padding ->")

    @Test
    fun theAttachButtonOpensTheTray() {
        assertTrue(
            "the chat must draw AttachTray",
            bottomBar.contains("AttachTray(")
        )
        assertTrue(
            "the tray belongs to the bottom bar, under the composer, not to the " +
                "screen's root as an overlay",
            bottomBar.indexOf("AttachTray(") > bottomBar.indexOf("InputBar(")
        )
    }

    @Test
    fun theTrayTakesItsHeightFromTheKeyboard() {
        assertTrue(
            "the tray is built to the keyboard's slot, so the composer holds still",
            bottomBar.contains("keyboardHeight = keyboardSlot")
        )
    }

    @Test
    fun theTrayOffersEveryAction() {
        val tray = bottomBar.substringAfter("AttachTray(")
        for (resource in listOf(
            "chat_media_camera", "chat_media_gallery", "chat_media_audio",
            "chat_media_location", "chat_media_contact"
        )) {
            assertTrue("$resource must have a tile", tray.contains("R.string.$resource"))
        }
    }

    @Test
    fun theOldSheetIsGone() {
        assertFalse(
            "AttachSheet is dead once the tray is wired in; leaving it behind ships " +
                "a second, unused attach UI",
            chat.contains("fun AttachSheet(")
        )
    }

    @Test
    fun openingTheTrayTakesTheKeyboardAway() {
        val attach = chat.substringAfter("onAttach = {").substringBefore("showEmojiButton")
        assertTrue("opening the tray must clear focus", attach.contains("focusManager.clearFocus()"))
        assertTrue("and hide the keyboard", attach.contains("keyboard?.hide()"))
        assertTrue(
            "closing it must hand the field back",
            attach.contains("fieldFocus.requestFocus()")
        )
    }

    @Test
    fun backClosesTheTrayBeforeTheChat() {
        assertTrue(
            "Back must close the tray instead of leaving the conversation",
            chat.contains("BackHandler(enabled = attachOpen) { attachOpen = false }")
        )
        val leaveChat = chat.indexOf("BackHandler(enabled = backHandled, onBack = ::leaveChat)")
        val trayBack = chat.indexOf("BackHandler(enabled = attachOpen)")
        assertTrue(
            "the tray's handler is registered after leave-chat, so it wins",
            trayBack > leaveChat
        )
    }

    @Test
    fun theComposerDoesNotReserveTheKeyboardUnderTheTray() {
        assertTrue(
            "with the tray open the composer must clear nothing, or the tray " +
                "lands on top of it",
            chat.contains("trayOnScreen -> 0.dp")
        )
        assertTrue(
            "the bar's height comes from the guarded IME inset, minus the strip " +
                "the bottom bar already pads",
            chat.contains("val keyboardInset = (imeBottom - navBottom).coerceAtLeast(0.dp)")
        )
    }

    @Test
    fun theComposerHoldsItsPlaceWhenTheKeyboardComesBack() {
        assertTrue(
            "the composer's inset must be the swap-aware one, not the raw keyboard inset",
            chat.contains("composerBottomInset = composerInset")
        )
        assertTrue(
            "closing the tray into the keyboard must hold the slot the tray vacated",
            chat.contains("keyboardSwap -> maxOf(keyboardInset, keyboardSlot.dp)")
        )
        assertTrue(
            "closing the tray by its own button asks for the keyboard back",
            chat.contains("keyboardSwap = true\n                                fieldFocus.requestFocus()")
        )
        assertTrue(
            "tapping the field while the tray is open is the same swap",
            chat.contains("if (attachOpen) {\n                                attachOpen = false\n                                keyboardSwap = true\n                            }")
        )
    }

    @Test
    fun theTrayLeavesBeforeTheKeyboardArrives() {
        assertTrue(
            "the tray's visibility is read back by the composer, so it must be " +
                "driven through a transition state",
            chat.contains("visibleState = tray")
        )
        assertTrue(
            "the tray fades out faster than the keyboard rises, so it never sits " +
                "over the keys",
            chat.contains("fadeOut(tween(90))")
        )
    }
}
