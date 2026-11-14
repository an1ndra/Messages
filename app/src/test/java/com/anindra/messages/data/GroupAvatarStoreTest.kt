package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding budget for a group picture.
 *
 * A photo picked from the gallery is often 12 MP; the avatar is drawn in a
 * 96 dp circle. Decoding at full size to shrink afterwards costs ~48 MB per
 * draw, so the sample size is chosen from the bounds before the pixels are
 * touched. The arithmetic is what this pins.
 */
class GroupAvatarStoreTest {

    @Test
    fun aPhotoThatAlreadyFitsIsNotSampled() {
        assertEquals(1, GroupAvatarStore.sampleSizeFor(512, 512))
        assertEquals(1, GroupAvatarStore.sampleSizeFor(300, 200))
    }

    @Test
    fun samplingHalvesUntilTheLongestSideFits() {
        assertEquals(2, GroupAvatarStore.sampleSizeFor(1024, 768))
        assertEquals(4, GroupAvatarStore.sampleSizeFor(2048, 1536))
        // 4000 / 4 = 1000 still does not fit; / 8 = 500 does.
        assertEquals(8, GroupAvatarStore.sampleSizeFor(4000, 3000))
    }

    @Test
    fun theLongestSideDecidesNotTheShortest() {
        // A tall panorama must be sampled on its height.
        assertEquals(8, GroupAvatarStore.sampleSizeFor(600, 2400))
    }

    @Test
    fun unreadableBoundsStillYieldAUsableSample() {
        assertEquals(1, GroupAvatarStore.sampleSizeFor(0, 0))
        assertEquals(1, GroupAvatarStore.sampleSizeFor(-1, 100))
    }

    @Test
    fun theSampleAlwaysLeavesSomethingToDecode() {
        var width = 100000
        while (width > 1) {
            val sample = GroupAvatarStore.sampleSizeFor(width, width)
            assertTrue("sample $sample for $width", width / sample >= 1)
            width /= 2
        }
    }

    @Test
    fun aStoredCopyIsNeverUpscaled() {
        // The cap is a maximum, not a target: a 64 px picture stays 64 px.
        assertTrue(GroupAvatarStore.MAX_PX >= 512)
    }
}
