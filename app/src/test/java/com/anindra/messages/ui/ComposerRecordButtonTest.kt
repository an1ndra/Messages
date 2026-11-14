package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * WhatsApp's composer: one accent-filled circle for Record, Send and the locked
 * send, with only the glyph changing. The accent fills the circle in both idle
 * states, so the swap recolours nothing, and the disc turns red for the length
 * of a note. The input pill's fill is no longer involved.
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
    fun theIdleRecordButtonUsesTheAccent() {
        assertTrue(
            "the fill must be decided once, by the button's own state",
            chat.contains("val usesAccent = ComposerButton.usesAccentFill(circleMode, recording)")
        )
        assertTrue(
            "the idle record disc must be the accent, like Send",
            chat.contains("val fillTarget = if (usesAccent) accent")
        )
        assertTrue(
            "its glyph must be the on-accent ink",
            chat.contains("val inkTarget = if (usesAccent) MaterialTheme.colorScheme.onPrimary")
        )
        assertTrue(
            "the record disc must not fall back to the input pill's fill",
            !chat.contains("else MaterialTheme.colorScheme.inputPill")
        )
    }

    @Test
    fun recordingRepaintsTheRecordButtonRed() {
        assertTrue(
            "recording must turn the record circle red",
            chat.contains("else MaterialTheme.colorScheme.recordActive")
        )
        assertTrue(
            "the glyph must switch to the ink that reads on that red",
            chat.contains("else MaterialTheme.colorScheme.onRecordActive")
        )
    }

    @Test
    fun theDiscBreathesWithTheVoice() {
        // Silent recording looked frozen; the disc carries a live meter behind
        // it, driven by the recorder's own amplitude.
        assertTrue(
            "the composer must take the microphone level",
            chat.contains("voiceLevel: Float = 0f")
        )
        assertTrue(
            "the meter must be drawn behind the disc while recording",
            chat.contains("HALO_GROWTH * voiceLevel")
        )
    }

    @Test
    fun theCircleSwapsMeaningWithAMotion() {
        // The circle stays put and only the glyph turns over, which is what makes
        // the swap read as WhatsApp's: one button changing its mind.
        assertTrue(
            "the glyph must swap through AnimatedContent",
            chat.contains("AnimatedContent(") && chat.contains("label = \"composerGlyph\"")
        )
        assertTrue(
            "the swap must pinch and spring, not just cut",
            chat.contains("scaleIn(initialScale = ComposerSwap.SCALE")
        )
        assertTrue(
            "and the outgoing glyph must turn a quarter",
            chat.contains("ComposerSwap.TURN_DEGREES")
        )
    }

    @Test
    fun theSendCircleKeepsTheAccent() {
        assertTrue(
            "the send arrow must be drawn by the shared circle",
            chat.contains("ComposerGlyph.Send -> Icon(")
        )
        assertTrue(
            "its glyph is inked against the disc, which is the accent",
            chat.contains("tint = discInk")
        )
    }
}
