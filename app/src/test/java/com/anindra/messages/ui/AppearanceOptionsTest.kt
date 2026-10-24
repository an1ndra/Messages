package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Appearance follow-ups from the #347 feedback: the redesigned interface is the
 * default, and the send circle is permanent but only live when there is
 * something to send.
 */
class AppearanceOptionsTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    @Test
    fun theRedesignedInterfaceIsTheDefault() {
        assertTrue(
            "a fresh install should get the new UI",
            read("src/main/java/com/anindra/messages/data/SettingsStore.kt")
                .contains("getBoolean(KEY_USE_NEW_UI, true)")
        )
    }

    @Test
    fun theSendButtonIsAlwaysOnScreenAndOnlyLiveWithSomethingToSend() {
        val chat = read("src/main/java/com/anindra/messages/ui/ChatScreen.kt")
        assertTrue(
            "the send button must not be hidden inside a content check",
            !chat.contains("if (draft.isNotBlank() || hasAttachments) {")
        )
        assertTrue(
            "the input bar must draw the send circle unconditionally",
            chat.contains("val ready = canSend(draft, hasAttachments)")
        )
        assertTrue(
            "an empty composer must not be able to fire a send",
            chat.contains(".combinedClickable(enabled = ready, onClick = onSend, onLongClick = onSchedule)")
        )
        assertTrue(
            "the circle keeps its full primary fill even when idle",
            chat.contains(".background(MaterialTheme.colorScheme.primary, CircleShape)")
        )
    }
}
