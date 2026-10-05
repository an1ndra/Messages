package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmsImageSizingTest {
    @Test
    fun leavesImageAloneWhenItAlreadyFits() {
        assertNull(MmsImageSizing.fitWithin(640, 480, 640, 480))
        assertNull(MmsImageSizing.fitWithin(100, 100, 640, 480))
    }

    @Test
    fun scalesDownToFitBothBounds() {
        val size = MmsImageSizing.fitWithin(4032, 3024, 640, 480)!!
        assertEquals(640, size.width)
        assertEquals(480, size.height)
    }

    @Test
    fun preservesAspectRatio() {
        val size = MmsImageSizing.fitWithin(1000, 500, 640, 480)!!
        assertEquals(640, size.width)
        assertEquals(320, size.height)
    }

    @Test
    fun treatsNonPositiveBoundsAsUnbounded() {
        assertNull(MmsImageSizing.fitWithin(8000, 6000, 0, 0))
        // Width unbounded, height capped.
        val size = MmsImageSizing.fitWithin(8000, 6000, 0, 480)!!
        assertEquals(640, size.width)
        assertEquals(480, size.height)
        // Height unbounded, width capped.
        val wide = MmsImageSizing.fitWithin(8000, 6000, 640, 0)!!
        assertEquals(640, wide.width)
        assertEquals(480, wide.height)
    }

    @Test
    fun ignoresUndecodableDimensions() {
        assertNull(MmsImageSizing.fitWithin(0, 0, 640, 480))
        assertNull(MmsImageSizing.fitWithin(-10, 100, 640, 480))
        assertNull(MmsImageSizing.fitWithin(100, 100, 640, 480))
    }

    @Test
    fun neverRoundsAnAxisDownToZero() {
        // Extreme aspect ratio: scaling to the width cap would zero out the height.
        val size = MmsImageSizing.fitWithin(10_000, 5, 640, 480)!!
        assertEquals(640, size.width)
        assertEquals(1, size.height)
    }

    @Test
    fun subsamplesOnlyWhenItStaysAtOrAboveTarget() {
        val target = MmsImageSizing.Size(640, 480)
        assertEquals(1, MmsImageSizing.sampleSize(640, 480, target))
        assertEquals(2, MmsImageSizing.sampleSize(1280, 960, target))
        assertEquals(4, MmsImageSizing.sampleSize(2560, 1920, target))
        // 8x decodes to exactly 640x480, still the target, so it is allowed;
        // 16x would land at 320x240, below it, so the loop stops at 8.
        assertEquals(8, MmsImageSizing.sampleSize(5120, 3840, target))
        assertEquals(1, MmsImageSizing.sampleSize(1024, 2000, MmsImageSizing.Size(640, 480)))
    }

    @Test
    fun subsamplingIsANoOpWithoutATarget() {
        assertEquals(1, MmsImageSizing.sampleSize(4000, 3000, null))
        assertEquals(1, MmsImageSizing.sampleSize(0, 0, MmsImageSizing.Size(640, 480)))
    }
}
