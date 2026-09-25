package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimSwitcherTest {

    private fun sim(id: Int, slot: Int, carrier: String? = null) =
        SimCard(id, slot, carrier, null, null, null, false)

    @Test
    fun cyclesAndWraps() {
        val sims = listOf(sim(1, 0, "T-Mobile"), sim(7, 1, "Vodafone"))
        assertEquals(7, SimSwitcher.next(1, sims)!!.subscriptionId)
        assertEquals(1, SimSwitcher.next(7, sims)!!.subscriptionId)
    }

    @Test
    fun fallsBackToFirstWhenSavedSimIsGone() {
        val sims = listOf(sim(1, 0, "T-Mobile"), sim(7, 1, "Vodafone"))
        assertEquals(1, SimSwitcher.next(-1, sims)!!.subscriptionId)
        assertEquals(1, SimSwitcher.next(99, sims)!!.subscriptionId)
    }

    @Test
    fun noSimsReturnsNull() {
        assertNull(SimSwitcher.next(1, emptyList()))
    }

    @Test
    fun switchStaysVisibleForTheWholeDraft() {
        assertFalse(SimSwitcher.shouldShowSwitch(0))
        assertFalse(SimSwitcher.shouldShowSwitch(1))
        assertTrue(SimSwitcher.shouldShowSwitch(2))
        assertTrue(SimSwitcher.shouldShowSwitch(3))
    }

    @Test
    fun selectedIndexResolvesAKnownSim() {
        val sims = listOf(sim(1, 0, "T-Mobile"), sim(7, 1, "Vodafone"))
        assertEquals(0, SimSwitcher.selectedIndex(1, sims))
        assertEquals(1, SimSwitcher.selectedIndex(7, sims))
    }

    @Test
    fun staleSimFallsBackToTheFirstSlotInsteadOfRenderingZero() {
        val sims = listOf(sim(1, 0, "T-Mobile"), sim(7, 1, "Vodafone"))
        assertEquals(0, SimSwitcher.selectedIndex(99, sims))
        assertEquals(0, SimSwitcher.selectedIndex(-1, sims))
    }

    @Test
    fun selectedIndexWithNoSims() {
        assertEquals(0, SimSwitcher.selectedIndex(1, emptyList()))
    }
}
