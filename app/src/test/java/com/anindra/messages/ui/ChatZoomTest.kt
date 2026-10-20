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
}
