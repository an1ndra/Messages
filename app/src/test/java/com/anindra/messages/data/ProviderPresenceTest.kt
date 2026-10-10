package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prune's namespacing: a local row is only ever matched against its own
 * transport's provider rows. Both sides of the comparison key through this,
 * so the live-set build and the doomed-row check cannot disagree about which
 * side of zero an id lives on.
 */
class ProviderPresenceTest {

    @Test
    fun smsIdsKeepTheirSign() {
        assertEquals(42L, ProviderPresence.key(MmsSupport.TRANSPORT_SMS, 42L))
    }

    @Test
    fun mmsIdsAreNamespacedNegative() {
        assertEquals(-42L, ProviderPresence.key(MmsSupport.TRANSPORT_MMS, 42L))
    }

    @Test
    fun anMmsRowNeverMatchesAnSmsProviderId() {
        val live = setOf(ProviderPresence.key(MmsSupport.TRANSPORT_SMS, 42L))
        assertFalse(live.contains(ProviderPresence.key(MmsSupport.TRANSPORT_MMS, 42L)))
    }

    @Test
    fun anMmsRowSurvivesWhileItsOwnProviderRowIsLive() {
        val live = setOf(ProviderPresence.key(MmsSupport.TRANSPORT_MMS, 42L))
        assertTrue(live.contains(ProviderPresence.key(MmsSupport.TRANSPORT_MMS, 42L)))
    }
}
