package com.anindra.messages.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bubble draws one bar per bucket of the decoded envelope. The arithmetic
 * that turns a peak list into bar heights is the part worth pinning: an
 * off-by-one there silently stretches or squashes the whole waveform.
 */
class VoiceWaveformTest {

    @Test
    fun resamplesAnyLengthIntoAFixedBarCount() {
        val raw = FloatArray(200) { it.toFloat() }
        val bars = VoiceWaveform.normalize(raw, 32)
        assertEquals(VoiceWaveform.BARS, bars.size)
    }

    @Test
    fun loudestBarIsFullHeight() {
        val bars = VoiceWaveform.normalize(floatArrayOf(1f, 4f, 2f, 40f), 4)
        assertEquals(1f, bars[3], 0.001f)
    }

    @Test
    fun aQuietStretchStaysVisible() {
        val bars = VoiceWaveform.normalize(floatArrayOf(100f, 0f, 0f, 100f), 4)
        assertTrue("a silent bar must not collapse to nothing", bars[1] > 0f)
        assertTrue(bars[1] < bars[0])
    }

    @Test
    fun silenceFallsBackToThePlaceholderShape() {
        val bars = VoiceWaveform.normalize(FloatArray(8), 8)
        assertEquals(8, bars.size)
        assertTrue(bars.all { it > 0f })
    }

    @Test
    fun anEmptyEnvelopeStillDrawsSomething() {
        assertEquals(VoiceWaveform.BARS, VoiceWaveform.normalize(FloatArray(0)).size)
    }

    @Test
    fun thePlaceholderIsStableForTheSameNote() {
        val a = VoiceWaveform.placeholder("content://mms/part/12")
        val b = VoiceWaveform.placeholder("content://mms/part/12")
        assertTrue(a.contentEquals(b))
        assertTrue(a.all { it > 0f })
    }
}
