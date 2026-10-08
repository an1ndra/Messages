package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class MmsSenderTest {

    @Test
    fun anImageIsReadUpToTheMemoryCapNotTheCarrierCap() {
        // It is shrunk to fit, so a 6 MB photo must reach the fitter even when
        // the carrier only takes 300 KB.
        assertEquals(
            MmsSender.IMAGE_READ_CAP_BYTES,
            MmsSender.readLimit("image/jpeg", 300 * 1024)
        )
    }

    @Test
    fun anythingElseIsReadUpToTheCarrierCap() {
        // A video is sent as it is, so more than the carrier takes can never be
        // sent and is not worth buffering.
        assertEquals(
            300L * 1024,
            MmsSender.readLimit("video/mp4", 300 * 1024)
        )
    }

    @Test
    fun aNonsenseCarrierCapReadsNothing() {
        assertEquals(0L, MmsSender.readLimit("audio/mpeg", -1))
    }
}
