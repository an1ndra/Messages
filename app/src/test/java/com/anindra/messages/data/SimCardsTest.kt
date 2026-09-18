package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimCardsTest {

    @Test
    fun debugFakeHasTwoSimsWithDistinctIdsAndSlots() {
        val sims = SimCards.debugFake()
        assertEquals(2, sims.size)
        assertEquals(setOf(0, 1), sims.map { it.slotIndex }.toSet())
        assertEquals(2, sims.map { it.subscriptionId }.toSet().size)
        val second = sims.first { it.slotIndex == 1 }
        assertEquals(7, second.subscriptionId)
        assertEquals("Vodafone", second.carrierName)
    }

    @Test
    fun overrideOnlyAppliesToDebuggableBuilds() {
        SimCards.setDebugOverride(true, debuggable = false)
        assertFalse(SimCards.isOverridden())
        SimCards.setDebugOverride(true, debuggable = true)
        assertTrue(SimCards.isOverridden())
        SimCards.setDebugOverride(false, debuggable = true)
        assertFalse(SimCards.isOverridden())
    }
}
