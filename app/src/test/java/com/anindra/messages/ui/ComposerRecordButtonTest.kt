package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The record button wears the composer's own fill; Send keeps the accent.
 *
 * Two circles sit in the same slot, and giving both the accent made the record
 * button read as a send button. The fill is a theme role (`inputPill`, the same
 * one the pill beside it uses) rather than a colour, so dynamic colour repaints
 * both together.
 */
class ComposerRecordButtonTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private val chat get() = File(
        app, "src/main/java/com/anindra/messages/ui/ChatScreen.kt"
    ).readText()

    @Test
    fun theRecordButtonUsesThePillsFill() {
        assertTrue(
            "the record circle must paint itself with the composer's fill role",
            chat.contains("else MaterialTheme.colorScheme.inputPill")
        )
    }

    @Test
    fun anArmedCancelRepaintsTheRecordButton() {
        assertTrue(
            "sliding to cancel must turn the record circle red",
            chat.contains("if (arm == VoiceArm.CANCEL) MaterialTheme.colorScheme.error")
        )
    }

    @Test
    fun theSendCircleKeepsTheAccent() {
        assertTrue(
            "the send arrow must stay on primary, or it stops looking like Send",
            chat.contains(".background(MaterialTheme.colorScheme.primary, CircleShape)")
        )
        assertTrue(
            "its glyph stays on onPrimary",
            chat.contains("Icons.AutoMirrored.Rounded.Send, null,\n                    tint = MaterialTheme.colorScheme.onPrimary")
        )
    }
}
