package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The record button follows Google Messages' own palette; Send keeps the accent.
 *
 * Measured off GM's composer: idle, the record disc is the inverse surface with
 * an inverse-ink glyph; while recording it is red with white ink. Both are theme
 * roles, so dynamic colour repaints them with the wallpaper, and the accent
 * stays reserved for Send.
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
    fun theIdleRecordButtonMatchesThePill() {
        // Google Messages' own idle composer: the record disc is the pill's own
        // fill (#1A2025 on its dark theme) with a #C0C7CD glyph.
        assertTrue(
            "the idle record disc must use the composer fill",
            chat.contains("else MaterialTheme.colorScheme.inputPill")
        )
        assertTrue(
            "its glyph must be the light variant ink that reads on it",
            chat.contains("else MaterialTheme.colorScheme.onSurfaceVariant")
        )
    }

    @Test
    fun recordingRepaintsTheRecordButtonRed() {
        assertTrue(
            "recording must turn the record circle red, as Google Messages does",
            chat.contains("if (recording) MaterialTheme.colorScheme.recordActive")
        )
        assertTrue(
            "the glyph must switch to the ink that reads on that red",
            chat.contains("if (recording) MaterialTheme.colorScheme.onRecordActive")
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
