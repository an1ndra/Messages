package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleEntranceTest {
    @Test
    fun onlyMessagesArrivingAfterTheBaselineAnimate() {
        assertTrue(BubbleEntrance.shouldAnimate(11L, 10L, alreadyAnimated = false))
        assertFalse(BubbleEntrance.shouldAnimate(10L, 10L, alreadyAnimated = false))
        assertFalse(BubbleEntrance.shouldAnimate(9L, 10L, alreadyAnimated = false))
    }

    @Test
    fun noBaselineMeansExistingHistoryDoesNotAnimate() {
        assertFalse(BubbleEntrance.shouldAnimate(10L, 0L, alreadyAnimated = false))
        assertFalse(BubbleEntrance.shouldAnimate(10L, -1L, alreadyAnimated = false))
    }

    @Test
    fun replayedRowsDoNotAnimateTwice() {
        assertFalse(BubbleEntrance.shouldAnimate(11L, 10L, alreadyAnimated = true))
    }

    @Test
    fun outgoingSlidesFromTheRightAndIncomingFromTheLeft() {
        assertTrue(BubbleEntrance.direction(isMe = true) > 0f)
        assertTrue(BubbleEntrance.direction(isMe = false) < 0f)
        assertEquals(100f, BubbleEntrance.translationX(0f, isMe = true, slidePx = 100f), 0.001f)
        assertEquals(-100f, BubbleEntrance.translationX(0f, isMe = false, slidePx = 100f), 0.001f)
        assertEquals(0f, BubbleEntrance.translationX(1f, isMe = true, slidePx = 100f), 0.001f)
    }

    @Test
    fun progressRunsFromSmallFadedToFullScaleOpaque() {
        assertEquals(0f, BubbleEntrance.alpha(0f), 0.001f)
        assertEquals(1f, BubbleEntrance.alpha(1f), 0.001f)
        assertEquals(BubbleEntrance.INITIAL_SCALE, BubbleEntrance.scale(0f), 0.001f)
        assertEquals(1f, BubbleEntrance.scale(1f), 0.001f)
        assertTrue(BubbleEntrance.scale(0f) < BubbleEntrance.scale(0.5f))
        assertTrue(BubbleEntrance.scale(0.5f) < BubbleEntrance.scale(1f))
    }

    @Test
    fun progressIsClamped() {
        assertEquals(0f, BubbleEntrance.alpha(-0.5f), 0.001f)
        assertEquals(1f, BubbleEntrance.alpha(1.5f), 0.001f)
        assertEquals(1f, BubbleEntrance.scale(2f), 0.001f)
        assertEquals(BubbleEntrance.INITIAL_SCALE, BubbleEntrance.scale(-2f), 0.001f)
    }
}
