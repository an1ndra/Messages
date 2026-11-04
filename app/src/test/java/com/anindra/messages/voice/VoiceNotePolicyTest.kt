package com.anindra.messages.voice

import com.anindra.messages.mms.SendReqBuilder
import com.anindra.messages.mms.spi.BudgetPolicy
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
    fun theDefaultCarrierCapLeavesRoomForThePduAndSmil() {
        // The raw cap is 307200 B, but the send path pays 12 KiB of header/SMIL
        // and two 512 B part allowances before the attachment gets anything.
        // The old raw-cap arithmetic advertised 61 s, which the fitter rejected.
        assertEquals(58, VoiceNotePolicy.maxSecondsForCarrier(307_200))
    }

    @Test
    fun theAdvertisedLengthAlwaysFitsTheAttachmentBudget() {
        val cap = 307_200
        val budget = BudgetPolicy.attachmentBudget(
            carrierMaxMessageSize = cap,
            captionBytes = 0,
            partCount = SendReqBuilder.partCount(""),
        )
        val seconds = VoiceNotePolicy.maxSecondsForCarrier(cap)
        assertTrue(VoiceNotePolicy.estimatedBytes(seconds) <= budget)
    }

    @Test
    fun aSmallerCarrierCapShortensTheRecording() {
        assertTrue(
            VoiceNotePolicy.maxSecondsForCarrier(102_400) <
                VoiceNotePolicy.maxSecondsForCarrier(307_200)
        )
        assertEquals(17, VoiceNotePolicy.maxSecondsForCarrier(102_400))
    }

    @Test
    fun anUnknownBudgetFallsBackToTheHardLimit() {
        assertEquals(VoiceNotePolicy.MAX_SECONDS, VoiceNotePolicy.maxSeconds(0))
        assertEquals(VoiceNotePolicy.MAX_SECONDS, VoiceNotePolicy.maxSeconds(-1))
        assertEquals(VoiceNotePolicy.MAX_SECONDS, VoiceNotePolicy.maxSecondsForCarrier(0))
        // A budget so small it rounds to nothing is still a recordable second:
        // the send fails with a notice rather than the mic refusing to open.
        assertEquals(VoiceNotePolicy.MIN_SECONDS, VoiceNotePolicy.maxSeconds(1_000))
    }

    @Test
    fun theCapNeverExceedsTheHardLimit() {
        assertEquals(VoiceNotePolicy.MAX_SECONDS, VoiceNotePolicy.maxSeconds(10_000_000))
        assertEquals(VoiceNotePolicy.MAX_SECONDS, VoiceNotePolicy.maxSecondsForCarrier(10_000_000))
    }

    @Test
    fun theEstimateIsTheSameArithmeticTheCapUses() {
        val seconds = VoiceNotePolicy.maxSecondsForCarrier(307_200)
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
