package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimSwitcherTest {
    private fun sim(id: Int, slot: Int, carrier: String? = null) =
        SimCard(id, slot, carrier, null, null, null, false)

    @Test
    fun iconReflectsCountAndSelection() {
        assertEquals(SimIcon.SIM_1, SimSwitcher.iconFor(1, 0))
        assertEquals(SimIcon.SIM_1, SimSwitcher.iconFor(2, 0))
        assertEquals(SimIcon.SIM_2, SimSwitcher.iconFor(2, 1))
        assertEquals(SimIcon.DUAL, SimSwitcher.iconFor(3, 0))
        assertEquals(SimIcon.DUAL, SimSwitcher.iconFor(3, 2))
    }

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
}
