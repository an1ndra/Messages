package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SimSelectionTest {
    private fun sim(id: Int, slot: Int, carrier: String? = null) =
        SimCard(id, slot, carrier, null, null, null, false)

    @Test
    fun keepsPersistedIdWhenStillPresent() {
        val sims = listOf(sim(1, 0, "T-Mobile"), sim(7, 1, "Vodafone"))
        assertEquals(7, SimSelection.effective(7, sims))
    }

    @Test
    fun defaultWhenPersistedIsDefault() {
        assertEquals(-1, SimSelection.effective(-1, listOf(sim(1, 0))))
    }

    @Test
    fun fallsBackToDefaultWhenSavedSimIsGone() {
        // SIM removed / fake dual-SIM turned off: must not become "Unknown SIM".
        assertEquals(-1, SimSelection.effective(7, listOf(sim(1, 0, "T-Mobile"))))
    }

    @Test
    fun fallsBackToDefaultWithNoSimsLoaded() {
        assertEquals(-1, SimSelection.effective(1, emptyList()))
    }
}
