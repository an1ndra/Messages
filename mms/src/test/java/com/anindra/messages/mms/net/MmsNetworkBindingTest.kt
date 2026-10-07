package com.anindra.messages.mms.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The binding's two JVM-visible decisions: which subscription pins the
 * request, and that nothing is requested at all when the platform offers no
 * connectivity manager.
 */
class MmsNetworkBindingTest {

    @Test
    fun aNamedSubscriptionPinsTheRequest() {
        assertTrue(MmsNetworkSpec(1).pinsSubscription)
        assertTrue(MmsNetworkSpec(2).pinsSubscription)
    }

    @Test
    fun theDefaultAndNoSubscriptionDoNotPin() {
        assertFalse(MmsNetworkSpec(0).pinsSubscription)
        assertFalse(MmsNetworkSpec(-1).pinsSubscription)
    }

    @Test
    fun withNoConnectivityManagerTheBindingRefusesRatherThanGuessing() {
        val binding = MmsNetworkBinding(
            connectivityManager = null,
            spec = MmsNetworkSpec(2),
            requestFactory = { error("a request must not be built without a manager") },
        )
        assertNull(binding.acquire(1))
    }
}
