package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatZoomTest {

    @Test
    fun aPinchOutGrowsTheText() {
        assertEquals(1.5f, ChatZoom.apply(1f, 1.5f), 0.001f)
        assertEquals(1.5f, ChatZoom.fontScale(1.5f), 0.001f)
    }

    @Test
    fun theIndicatorReportsThePercentageActuallyRendered() {
        assertEquals(100, ChatZoom.percent(1f))
        assertEquals(150, ChatZoom.percent(1.5f))
        assertEquals(70, ChatZoom.percent(0.1f))
        assertEquals(250, ChatZoom.percent(9f))
    }

    @Test
    fun theRangeIsWideEnoughToBeUseful() {
        // The first cut stopped at 2x, which testers found cramped next to
        // Google Messages.
        assertTrue(ChatZoom.MAX >= 2.5f)
        assertTrue(ChatZoom.MIN <= 0.8f)
    }

    @Test
    fun itCannotShrinkBelowTheFloorOrGrowPastTheCeiling() {
        assertEquals(ChatZoom.MIN, ChatZoom.apply(1f, 0.1f), 0.001f)
        assertEquals(ChatZoom.MAX, ChatZoom.apply(1f, 10f), 0.001f)
        // Clamping is stable: pinching further at the limit changes nothing.
        assertEquals(ChatZoom.MAX, ChatZoom.apply(ChatZoom.MAX, 2f), 0.001f)
        assertEquals(ChatZoom.MIN, ChatZoom.apply(ChatZoom.MIN, 0.5f), 0.001f)
    }

    @Test
    fun theRenderedScaleIsClampedEvenForAStoredOutOfRangeValue() {
        // A value restored from a saved instance state must not paint at 5x.
        assertEquals(ChatZoom.MAX, ChatZoom.fontScale(5f), 0.001f)
        assertEquals(ChatZoom.MIN, ChatZoom.fontScale(0.2f), 0.001f)
    }

    @Test
    fun defaultIsNoZoom() {
        assertTrue(ChatZoom.isDefault(ChatZoom.DEFAULT))
        assertFalse(ChatZoom.isDefault(1.4f))
    }

    @Test
    fun theAnchorIsTheRowUnderTheFinger() {
        val items = listOf(
            ChatZoom.Item(0, 0, 100),
            ChatZoom.Item(1, 100, 200),
            ChatZoom.Item(2, 300, 50)
        )
        assertEquals(ChatZoom.Anchor(1, 0.25f), ChatZoom.anchorAt(150f, items))
        assertEquals(ChatZoom.Anchor(0, 0f), ChatZoom.anchorAt(0f, items))
        assertEquals(null, ChatZoom.anchorAt(10f, emptyList()))
    }

    @Test
    fun aFingerInTheGapBetweenBubblesPicksTheNearestRow() {
        val items = listOf(ChatZoom.Item(0, 0, 100), ChatZoom.Item(2, 400, 50))
        assertEquals(2, ChatZoom.anchorAt(380f, items)?.index)
        assertEquals(0, ChatZoom.anchorAt(120f, items)?.index)
    }

    @Test
    fun theAnchoredPointStaysUnderTheFingerAcrossAScaleChange() {
        // Finger a quarter of the way down a row that starts at y=200 and is 400
        // tall, i.e. 300px from the top of the viewport.
        val finger = 300f
        val anchor = ChatZoom.anchorAt(finger, listOf(ChatZoom.Item(7, 200, 400)))!!
        assertEquals(ChatZoom.Anchor(7, 0.25f), anchor)

        // The row re-laid out at 800px tall after the pinch.
        val delta = ChatZoom.anchorScroll(finger, anchor, ChatZoom.Item(7, 200, 800))
        val offsetAfter = 200 - delta
        assertEquals(finger, offsetAfter + anchor.fraction * 800f, 0.01f)

        // Nothing moved, nothing to correct.
        assertEquals(0f, ChatZoom.anchorScroll(finger, anchor, ChatZoom.Item(7, 200, 400)), 0.01f)
    }

    @Test
    fun oneDoubleTapZoomsInAndTheNextComesBack() {
        assertEquals(ChatZoom.DOUBLE_TAP, ChatZoom.doubleTapTarget(ChatZoom.DEFAULT), 0.001f)
        assertEquals(ChatZoom.DEFAULT, ChatZoom.doubleTapTarget(ChatZoom.DOUBLE_TAP), 0.001f)
        assertEquals(ChatZoom.DEFAULT, ChatZoom.doubleTapTarget(ChatZoom.MAX), 0.001f)
        assertTrue(ChatZoom.DOUBLE_TAP <= ChatZoom.MAX)
    }

    @Test
    fun theSpreadIsTheDistanceBetweenTheFingers() {
        assertEquals(5f, ChatZoom.spread(3f, 4f), 0.001f)
        assertEquals(0f, ChatZoom.spread(0f, 0f), 0.001f)
    }

    @Test
    fun aPinchIsMeasuredFromTheSpreadTheGestureStartedAt() {
        // Finger distance doubles -> the text doubles. Measured from the moment
        // the second finger landed, so no part of the pinch is thrown away by a
        // touch-slop gate (#348).
        assertEquals(2f, ChatZoom.pinch(1f, 200f, 400f), 0.001f)
        assertEquals(1.5f, ChatZoom.pinch(1f, 200f, 300f), 0.001f)
        assertEquals(0.7f, ChatZoom.pinch(1f, 300f, 210f), 0.001f)
        // ...and then the usual bounds.
        assertEquals(ChatZoom.MAX, ChatZoom.pinch(1f, 200f, 900f), 0.001f)
        assertEquals(ChatZoom.MIN, ChatZoom.pinch(1f, 400f, 40f), 0.001f)
        // A pinch that starts from an already zoomed chat multiplies that zoom.
        assertEquals(2.4f, ChatZoom.pinch(1.2f, 100f, 200f), 0.001f)
        assertEquals(ChatZoom.MAX, ChatZoom.pinch(1.5f, 100f, 200f), 0.001f)
    }

    @Test
    fun thePinchBaselineOutlivesTheGestureHandler() {
        // Compose restarts the pointerInput block whenever its lambda changes,
        // and the chat recomposes on every zoom step, so the baseline must live
        // outside it: keeping it in the block turned a 3.3x pinch into 1.9x.
        val pinch = ChatZoom.Pinch()
        assertFalse(pinch.tracking)

        pinch.begin(240f, 1f)
        assertTrue(pinch.tracking)
        assertEquals(1.25f, pinch.zoomFor(300f), 0.001f)
        // A restart re-reads the same object: same baseline, more zoom.
        assertEquals(1.5f, pinch.zoomFor(360f), 0.001f)
        assertEquals(2.5f, pinch.zoomFor(800f), 0.001f)

        pinch.clear()
        assertFalse(pinch.tracking)
        assertEquals(ChatZoom.DEFAULT, pinch.zoomFor(800f), 0.001f)
    }
}
