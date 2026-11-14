package com.anindra.messages.ui

import com.anindra.messages.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recording bar has two states, decided by one value (#364): a note being
 * held, and a slide that has gone far enough to drop it on release.
 */
class VoiceBarTest {

    @Test
    fun aHeldNoteOffersTheSlide() {
        val mode = VoiceBar.modeFor(VoiceArm.NONE)
        assertEquals(VoiceBarMode.HOLD, mode)
        assertEquals(R.string.chat_voice_slide_cancel, VoiceBar.hintRes(mode))
    }

    @Test
    fun anArmedCancelAsksToRelease() {
        val mode = VoiceBar.modeFor(VoiceArm.CANCEL)
        assertEquals(VoiceBarMode.CANCEL, mode)
        assertEquals(R.string.chat_voice_release_cancel, VoiceBar.hintRes(mode))
    }

    @Test
    fun theBarFollowsTheFingerLeftwardsOnly() {
        assertEquals(0f, VoiceBar.slideOffset(0f, capPx = 100f), 0.01f)
        assertTrue("a rightward drag must not carry the bar", VoiceBar.slideOffset(80f, capPx = 100f) <= 0f)
        assertEquals(-40f, VoiceBar.slideOffset(-100f, capPx = 100f), 0.01f)
        assertEquals("clamped so the row cannot leave the pill", -100f, VoiceBar.slideOffset(-900f, capPx = 100f), 0.01f)
    }

    @Test
    fun theBubbleIsQuietUntilTheFingerPullsIt() {
        assertEquals(0f, VoiceBar.bubbleProgress(0f, thresholdPx = 100f), 0.01f)
        assertEquals("a rightward drag must not inflate it", 0f, VoiceBar.bubbleProgress(60f, thresholdPx = 100f), 0.01f)
        assertEquals(0.5f, VoiceBar.bubbleProgress(-50f, thresholdPx = 100f), 0.01f)
    }

    @Test
    fun theBubbleIsFullyPulledAtTheArmAndNoFurther() {
        assertEquals(1f, VoiceBar.bubbleProgress(-100f, thresholdPx = 100f), 0.01f)
        assertEquals("a longer slide cannot overstretch it", 1f, VoiceBar.bubbleProgress(-900f, thresholdPx = 100f), 0.01f)
        assertEquals("a zero threshold is already armed", 1f, VoiceBar.bubbleProgress(0f, thresholdPx = 0f), 0.01f)
    }

    @Test
    fun theBubbleStretchesWideAndGivesVertically() {
        assertEquals(1f, VoiceBar.bubbleScaleX(0f), 0.001f)
        assertEquals(1f, VoiceBar.bubbleScaleY(0f), 0.001f)
        assertTrue("the pull must widen the bubble", VoiceBar.bubbleScaleX(1f) > 1f)
        assertTrue("the pull must flatten the bubble", VoiceBar.bubbleScaleY(1f) < 1f)
        assertTrue(
            "the give stays smaller than the stretch",
            VoiceBar.bubbleScaleY(1f) < 1f && (1f - VoiceBar.bubbleScaleY(1f)) < (VoiceBar.bubbleScaleX(1f) - 1f)
        )
    }

    @Test
    fun theBubblePopsInFromSmallerThanRest() {
        assertTrue(VoiceBar.BUBBLE_POP_FROM < 1f)
        assertTrue("a bigger start would not read as a pop", VoiceBar.BUBBLE_POP_FROM > 0.5f)
    }
}
