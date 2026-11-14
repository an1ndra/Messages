package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picture viewer's pinch rules (#460).
 *
 * These are the numbers a gesture actually produces, so they are pinned here
 * rather than inferred from a screenshot: the fitted picture must never shrink
 * below the whole frame, must not be draggable off its own edge, and a pinch
 * has to hold the point under the fingers.
 */
class ViewerZoomTest {

    private val epsilon = 0.001f

    @Test
    fun aPinchCannotShrinkThePictureBelowFit() {
        assertEquals(ViewerZoom.MIN, ViewerZoom.apply(1f, 0.4f), epsilon)
        assertEquals(ViewerZoom.MIN, ViewerZoom.apply(2f, 0.1f), epsilon)
    }

    @Test
    fun aPinchStopsAtTheMaximum() {
        assertEquals(ViewerZoom.MAX, ViewerZoom.apply(4f, 2f), epsilon)
        assertEquals(ViewerZoom.MAX, ViewerZoom.apply(1f, 99f), epsilon)
    }

    @Test
    fun aPinchInBetweenMultiplies() {
        assertEquals(2f, ViewerZoom.apply(1f, 2f), epsilon)
        assertEquals(3f, ViewerZoom.apply(2f, 1.5f), epsilon)
    }

    @Test
    fun doubleTapJumpsToTheZoomAndBackToFit() {
        assertEquals(ViewerZoom.DOUBLE_TAP, ViewerZoom.doubleTapTarget(ViewerZoom.MIN), epsilon)
        assertEquals(ViewerZoom.MIN, ViewerZoom.doubleTapTarget(ViewerZoom.DOUBLE_TAP), epsilon)
        assertEquals(ViewerZoom.MIN, ViewerZoom.doubleTapTarget(ViewerZoom.MAX), epsilon)
    }

    @Test
    fun aFittedPictureCannotBeDraggedAtAll() {
        assertEquals(0f, ViewerZoom.maxPan(1f, 1000f), epsilon)
        assertEquals(0f, ViewerZoom.clampPan(320f, 1f, 1000f), epsilon)
        assertEquals(0f, ViewerZoom.clampPan(-320f, 1f, 1000f), epsilon)
    }

    @Test
    fun aZoomedPictureStopsAtItsOwnEdge() {
        // At 2x, a 1000px axis leaves 500px of overhang on each side: the
        // picture's edge reaches the screen's edge and no further.
        assertEquals(500f, ViewerZoom.maxPan(2f, 1000f), epsilon)
        assertEquals(500f, ViewerZoom.clampPan(900f, 2f, 1000f), epsilon)
        assertEquals(-500f, ViewerZoom.clampPan(-900f, 2f, 1000f), epsilon)
        assertEquals(120f, ViewerZoom.clampPan(120f, 2f, 1000f), epsilon)
    }

    @Test
    fun thePanLimitGrowsWithTheZoom() {
        // At 5x a 1000px axis is 5000px of picture, so 2000px hangs off each
        // side — capping at half the viewport would stop the drag a quarter of
        // the way across the picture.
        assertEquals(2000f, ViewerZoom.maxPan(5f, 1000f), epsilon)
        assertEquals(2000f, ViewerZoom.clampPan(9000f, 5f, 1000f), epsilon)
    }

    @Test
    fun aPinchHoldsThePointUnderTheFingers() {
        // Fingers 100px right of centre, zooming 1x -> 2x with no drag. The
        // content under them must stay put: offset' + 2 * 100 == 100.
        val pan = ViewerZoom.anchoredPan(
            pan = 0f, offset = 0f, centroid = 100f, scale = 1f, next = 2f
        )
        assertEquals(-100f, pan, epsilon)
        assertEquals(100f, pan + 2f * 100f, epsilon)

        // From an already-panned picture, the drag is added on top.
        val dragged = ViewerZoom.anchoredPan(
            pan = 30f, offset = -100f, centroid = 100f, scale = 2f, next = 2f
        )
        assertEquals(-70f, dragged, epsilon)
    }

    @Test
    fun theViewerDoesNotTrustAGestureItCannotPlace() {
        // A centroid far outside a zero-sized viewport must still land on a
        // legal pan, or the picture would fly off screen before layout.
        val pan = ViewerZoom.anchoredPan(
            pan = 0f, offset = 0f, centroid = 4000f, scale = 1f, next = 3f
        )
        assertEquals(0f, ViewerZoom.clampPan(pan, 3f, 0f), epsilon)
        assertTrue(ViewerZoom.maxPan(3f, 0f) == 0f)
    }
}
