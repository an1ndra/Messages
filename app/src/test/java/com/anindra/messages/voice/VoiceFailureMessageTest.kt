package com.anindra.messages.voice

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The voice failure toast has to name the failure the user can act on (#364). */
class VoiceFailureMessageTest {

    private fun sourceOf(relative: String): String =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relative) }
            .firstOrNull { it.isFile }
            ?.readText()
            ?: error("$relative not found from ${File("").absolutePath}")

    @Test
    fun aTapAndARealFailureAreDifferentOutcomes() {
        val recorder = sourceOf(
            "app/src/main/java/com/anindra/messages/voice/VoiceRecorder.kt"
        )
        assertTrue(recorder.contains("data object TooShort"))
        assertTrue(recorder.contains("data object Failed"))
        assertTrue(recorder.contains("durationMs < MIN_RECORD_MS"))
    }

    @Test
    fun theComposerUsesTheOutcomeItWasGiven() {
        val chat = sourceOf("app/src/main/java/com/anindra/messages/ui/ChatScreen.kt")
        assertTrue(chat.contains("VoiceRecorder.FinishResult.TooShort ->"))
        assertTrue(chat.contains("VoiceRecorder.FinishResult.Failed ->"))
        assertTrue(chat.contains("R.string.chat_voice_too_short"))
        assertNotEquals(
            "a too-short press must not say the recording could not start",
            chat.indexOf("VoiceRecorder.FinishResult.TooShort ->") ,
            chat.lastIndexOf("R.string.chat_voice_start_failed")
        )
    }
}
