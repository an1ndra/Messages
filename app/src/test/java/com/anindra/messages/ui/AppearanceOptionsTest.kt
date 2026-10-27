package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Appearance behaviour: the redesigned interface is the default, and the send
 * button follows Google Messages — it disappears (shrink + fade) while there is
 * nothing to send, keeps its layout footprint so the field does not jump, and
 * cannot fire a send while empty.
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
    fun theSendButtonDisappearsWhenEmptyButKeepsItsFootprint() {
        val chat = read("src/main/java/com/anindra/messages/ui/ChatScreen.kt")
        assertTrue(
            "the send button must not be hidden inside a content check",
            !chat.contains("if (draft.isNotBlank() || hasAttachments) {")
        )
        assertTrue(
            "the input bar must resolve readability through canSend()",
            chat.contains("val ready = canSend(draft, hasAttachments)")
        )
        assertTrue(
            "an empty composer must not be able to fire a send",
            chat.contains(".combinedClickable(enabled = ready, onClick = onSend, onLongClick = onSchedule)")
        )
        assertTrue(
            "the idle state must animate away, not sit there faded",
            chat.contains("targetValue = if (ready) 1f else 0f")
        )
        assertTrue(
            "the animation must keep the layout footprint (graphicsLayer, not size removal)",
            Regex("""\.graphicsLayer\s*\{""").containsMatchIn(chat)
        )
        assertTrue(
            "reduce motion must skip the shrink-and-fade",
            chat.contains("motionTween(sendReduceMotion, Motion.DURATION_SHORT4)")
        )
    }
}
