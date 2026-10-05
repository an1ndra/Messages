package com.anindra.messages.mms.fit

import com.anindra.messages.mms.spi.BudgetPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSizingTest {

    @Test
    fun anImageAlreadyWithinBoundsIsLeftAlone() {
        assertEquals(null, ImageSizing.fitWithin(100, 100, 640, 480))
        assertEquals(null, ImageSizing.fitWithin(640, 480, 640, 480))
    }

    @Test
    fun bothBoundsUnboundedMeansNoResize() {
        assertEquals(null, ImageSizing.fitWithin(4000, 3000, 0, 0))
        assertEquals(null, ImageSizing.fitWithin(4000, 3000, -1, -1))
    }

    @Test
    fun theTighterOfTheTwoBoundsWins() {
        // Width allows 640/4000 = 0.16, height allows 480/1000 = 0.48. Width is
        // tighter, so it sets the scale and height is dragged down with it.
        assertEquals(ImageSizing.Size(640, 160), ImageSizing.fitWithin(4000, 1000, 640, 480))
    }

    @Test
    fun scalingIsProportionalSoNeitherAxisIsDistorted() {
        val size = ImageSizing.fitWithin(4000, 1000, 640, 480)!!
        assertEquals(640.0 / 4000.0, size.width.toDouble() / 4000, 0.001)
        assertEquals(640.0 / 4000.0, size.height.toDouble() / 1000, 0.001)
    }

    @Test
    fun anExtremeAspectRatioNeverRoundsAnAxisToZero() {
        val size = ImageSizing.fitWithin(10_000, 3, 640, 480)!!
        assertTrue(size.width >= 1)
        assertTrue(size.height >= 1)
    }

    @Test
    fun unmeasurableDimensionsAreNotResized() {
        assertEquals(null, ImageSizing.fitWithin(0, 100, 640, 480))
        assertEquals(null, ImageSizing.fitWithin(100, 0, 640, 480))
        assertEquals(null, ImageSizing.fitWithin(-1, -1, 640, 480))
    }

    @Test
    fun samplingStopsBeforeItWouldDecodeBelowTheTarget() {
        // 900x600 into a 100x100 target: 450x300, 225x150, 112x75 all clear it on
        // both axes, but the next step down is 56x37 which does not.
        assertEquals(4, ImageSizing.sampleSize(900, 600, ImageSizing.Size(100, 100)))
        // A wider target tolerates one more step.
        assertEquals(8, ImageSizing.sampleSize(900, 600, ImageSizing.Size(100, 70)))
        // A target at or above the source needs no subsampling at all.
        assertEquals(1, ImageSizing.sampleSize(900, 600, ImageSizing.Size(900, 600)))
    }

    @Test
    fun samplingIsANoOpWithoutATarget() {
        assertEquals(1, ImageSizing.sampleSize(900, 600, null))
        assertEquals(1, ImageSizing.sampleSize(0, 0, ImageSizing.Size(10, 10)))
    }
}
