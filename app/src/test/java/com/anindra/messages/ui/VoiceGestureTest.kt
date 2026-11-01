package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The record gesture has two exits and one running drag to decide them with, so
 * the decision is a pure function: sliding left drops the note, swiping up keeps
 * it running hands-free.
 */
class VoiceGestureTest {

    private val threshold = 100f

    @Test
    fun aSmallWobbleArmsNothing() {
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(0f, 0f, threshold))
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(-30f, -40f, threshold))
        // Coming back towards the button re-arms nothing.
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(20f, 30f, threshold))
    }

    @Test
    fun slidingLeftArmsTheCancel() {
        assertEquals(VoiceArm.CANCEL, VoiceGesture.armFor(-120f, 0f, threshold))
        assertEquals(VoiceArm.CANCEL, VoiceGesture.armFor(-threshold, 0f, threshold))
    }

    @Test
    fun swipingUpLocksTheRecording() {
        assertEquals(VoiceArm.LOCK, VoiceGesture.armFor(0f, -120f, threshold))
        assertEquals(VoiceArm.LOCK, VoiceGesture.armFor(0f, -threshold, threshold))
    }

    @Test
    fun aDiagonalUpLeftCancelsRatherThanLocks() {
        // Throwing a note away must be the easier of the two to reach by
        // accident, so the cancel wins when both thresholds are crossed.
        assertEquals(VoiceArm.CANCEL, VoiceGesture.armFor(-150f, -150f, threshold))
    }

    @Test
    fun theThresholdIsTheSameDistanceUpAsLeft() {
        assertEquals(72, VoiceGesture.THRESHOLD_DP)
    }
}
