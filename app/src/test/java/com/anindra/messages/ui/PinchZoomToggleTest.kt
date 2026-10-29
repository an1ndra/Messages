package com.anindra.messages.ui

import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pinch-to-zoom is an option in Inbox settings, the way Google Messages puts
 * its conversation-text zoom there. Turning it off must remove the gesture, not
 * just ignore its result: a captured two-finger drag would stop scrolling.
 */
class PinchZoomToggleTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val chat get() = read("src/main/java/com/anindra/messages/ui/ChatScreen.kt")
    private val inbox get() = read("src/main/java/com/anindra/messages/ui/InboxSettingsScreen.kt")
    private val store get() = read("src/main/java/com/anindra/messages/data/SettingsStore.kt")

    @Test
    fun theOptionExistsInInboxSettings() {
        assertTrue(
            "Inbox settings must carry the switch",
            inbox.contains("checked = vm.settings.pinchZoomEnabled")
        )
        assertTrue(
            "and write it back",
            inbox.contains("onChecked = { vm.settings.pinchZoomEnabled = it }")
        )
    }

    @Test
    fun itDefaultsToOn() {
        assertTrue(
            "zoom must be on for a fresh install; it shipped in #348",
            store.contains("prefs.getBoolean(KEY_PINCH_ZOOM, true)")
        )
    }

    @Test
    fun turningItOffRemovesTheGesture() {
        assertTrue(
            "the handler must only be installed when the option is on",
            chat.contains("if (pinchZoomEnabled) pinchToZoom()")
        )
        assertTrue(
            "and keyed on the option, so flipping it reinstalls the handler",
            chat.contains(".pointerInput(conversationId, pinchZoomEnabled)")
        )
        assertTrue(
            "and the setting must be read in the chat",
            chat.contains("val pinchZoomEnabled = vm.settings.pinchZoomEnabled")
        )
    }
}
