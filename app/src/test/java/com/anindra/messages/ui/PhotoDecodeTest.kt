package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoDecodeTest {

    @Test
    fun keepsSmallImagesUnsampled() {
        assertEquals(1, PhotoDecode.sampleSize(200, 200, 144))
        assertEquals(1, PhotoDecode.sampleSize(288, 288, 144))
    }

    @Test
    fun downsamplesLargeImages() {
        assertEquals(2, PhotoDecode.sampleSize(500, 500, 144))
        assertEquals(8, PhotoDecode.sampleSize(1200, 1200, 144))
        assertEquals(8, PhotoDecode.sampleSize(1200, 600, 144))
    }

    @Test
    fun handlesInvalidBounds() {
        assertEquals(1, PhotoDecode.sampleSize(0, 0, 144))
        assertEquals(1, PhotoDecode.sampleSize(-1, 100, 144))
        assertEquals(1, PhotoDecode.sampleSize(100, 100, 0))
    }
}
