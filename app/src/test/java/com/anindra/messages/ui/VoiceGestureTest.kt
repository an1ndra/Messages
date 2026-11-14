package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The record gesture has two exits and one running drag to decide them with:
 * sliding left drops the note, anything else sends on release. Swiping up used
 * to lock the note hands-free; that exit is gone, so an upward drag must arm
 * nothing. The decision is a pure function so the sensitivity can be pinned.
 */
class VoiceGestureTest {

    private val threshold = 100f

    @Test
    fun aSmallWobbleArmsNothing() {
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(0f, threshold))
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(-30f, threshold))
        // Coming back towards the button re-arms nothing.
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(20f, threshold))
    }

    @Test
    fun slidingLeftArmsTheCancel() {
        assertEquals(VoiceArm.CANCEL, VoiceGesture.armFor(-120f, threshold))
        assertEquals(VoiceArm.CANCEL, VoiceGesture.armFor(-threshold, threshold))
    }

    @Test
    fun aStraightUpSwipeArmsNothingNowThatLockIsGone() {
        // The gesture only reads the horizontal travel; a hand that drifts up
        // mid-sentence keeps recording and sends on release.
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(0f, threshold))
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(5f, threshold))
    }

    @Test
    fun aDiagonalUpLeftStillCancels() {
        assertEquals(VoiceArm.CANCEL, VoiceGesture.armFor(-150f, threshold))
    }

    @Test
    fun theGestureAsksForADeliberatePress() {
        assertTrue("a tap must not open the microphone", VoiceGesture.HOLD_DELAY_MS >= 200)
    }
}
