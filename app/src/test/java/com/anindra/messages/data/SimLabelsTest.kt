package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SimLabelsTest {

    @Test
    fun defaultWhenNoSimSelected() {
        assertEquals(SimLabel.Default, SimLabels.resolve(-1, null, null))
    }

    @Test
    fun carrierAndSlotWhenBothAvailable() {
        assertEquals(SimLabel.Carrier("Vodafone", 1), SimLabels.resolve(7, 0, "Vodafone"))
        assertEquals(SimLabel.Carrier("Airtel", 2), SimLabels.resolve(3, 1, "Airtel"))
    }

    @Test
    fun slotWhenCarrierMissingOrBlank() {
        assertEquals(SimLabel.Slot(2), SimLabels.resolve(3, 1, null))
        assertEquals(SimLabel.Slot(2), SimLabels.resolve(3, 1, "   "))
    }

    @Test
    fun unknownInsteadOfRawSubscriptionId() {
        assertEquals(SimLabel.Unknown, SimLabels.resolve(7, null, null))
    }

    @Test
    fun statelessLabelsAreSingletons() {
        assertSame(SimLabel.Default, SimLabels.resolve(-1, null, null))
        assertSame(SimLabel.Unknown, SimLabels.resolve(7, null, null))
    }
}
