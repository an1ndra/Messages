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
        // Width allows 640/4000, height allows 480/1000; height is tighter.
        assertEquals(ImageSizing.Size(640, 480), ImageSizing.fitWithin(4000, 1000, 640, 480))
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
    fun samplingKeepsTheDecodeAtOrAboveTheTarget() {
        val target = ImageSizing.Size(100, 100)
        assertEquals(1, ImageSizing.sampleSize(900, 600, target))
        assertEquals(8, ImageSizing.sampleSize(900, 600, ImageSizing.Size(100, 70)))
    }

    @Test
    fun samplingIsANoOpWithoutATarget() {
        assertEquals(1, ImageSizing.sampleSize(900, 600, null))
        assertEquals(1, ImageSizing.sampleSize(0, 0, ImageSizing.Size(10, 10)))
    }
}
