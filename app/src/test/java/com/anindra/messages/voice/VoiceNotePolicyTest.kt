package com.anindra.messages.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recorder stops at the carrier's size budget, so the length it allows has
 * to be derived from that budget rather than picked by hand. These are the
 * numbers the Diagnostics sheet prints, and the ones test-voice-message.sh
 * reads back.
 */
class VoiceNotePolicyTest {

    @Test
    fun aMinuteOfSpeechFitsTheDefaultCarrierCap() {
        // An unexplained 30s cap is the usual reason a voice note is useless;
        // the default AOSP MMS cap is 300 KB, which is a minute of 32 kbps AAC.
        assertEquals(61, VoiceNotePolicy.maxSeconds(307_200))
    }

    @Test
    fun aSmallerCarrierCapShortensTheRecording() {
        assertTrue(VoiceNotePolicy.maxSeconds(102_400) < VoiceNotePolicy.maxSeconds(307_200))
        assertEquals(20, VoiceNotePolicy.maxSeconds(102_400))
    }

    @Test
    fun anUnknownBudgetFallsBackToTheHardLimit() {
        assertEquals(VoiceNotePolicy.MAX_SECONDS, VoiceNotePolicy.maxSeconds(0))
        assertEquals(VoiceNotePolicy.MAX_SECONDS, VoiceNotePolicy.maxSeconds(-1))
        // A budget so small it rounds to nothing is still a recordable second:
        // the send fails with a notice rather than the mic refusing to open.
        assertEquals(VoiceNotePolicy.MIN_SECONDS, VoiceNotePolicy.maxSeconds(1_000))
    }

    @Test
    fun theCapNeverExceedsTheHardLimit() {
        assertEquals(VoiceNotePolicy.MAX_SECONDS, VoiceNotePolicy.maxSeconds(10_000_000))
    }

    @Test
    fun theEstimateIsTheSameArithmeticTheCapUses() {
        val seconds = VoiceNotePolicy.maxSeconds(307_200)
        assertTrue(VoiceNotePolicy.estimatedBytes(seconds) <= 307_200)
        assertEquals(VoiceNotePolicy.bytesPerSecond(), VoiceNotePolicy.estimatedBytes(1))
        assertEquals(0, VoiceNotePolicy.estimatedBytes(-5))
    }

    @Test
    fun labelsReadLikeAPlayer() {
        assertEquals("0:00", VoiceNotePolicy.label(0))
        assertEquals("0:07", VoiceNotePolicy.label(7_400))
        assertEquals("1:00", VoiceNotePolicy.label(60_000))
        assertEquals("2:01", VoiceNotePolicy.label(121_500))
        assertEquals("0:00", VoiceNotePolicy.label(-100))
        // The recording bar counts up in the same "00:03" shape Google
        // Messages uses, so the row does not jump at the ten-second mark.
        assertEquals("00:03", VoiceNotePolicy.elapsedLabel(3_400))
        assertEquals("01:01", VoiceNotePolicy.elapsedLabel(61_000))
    }
}
