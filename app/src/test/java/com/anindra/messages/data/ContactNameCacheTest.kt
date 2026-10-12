package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The conversation list shows the name stored on the conversation row, which is
 * only rewritten from a phone_lookup result. #317: a contact renamed in the
 * phone book kept the old name for 20 minutes to 2 hours because nothing
 * re-resolved it until a resume cleared a 5-minute gate.
 */
class ContactNameCacheTest {

    private var now = 1_000L
    private val cache = ContactNameCache(ttlMs = 5_000L, clock = { now })

    @Test
    fun aResolvedNameIsReusedWithinTheTtl() {
        var lookups = 0
        val resolve: (String) -> String? = { lookups++; "Alice" }
        assertEquals("Alice", cache.getOrResolve("+15551230001", resolve))
        assertEquals("Alice", cache.getOrResolve("+15551230001", resolve))
        assertEquals("one provider hit inside the TTL", 1, lookups)
    }

    @Test
    fun anEntryIsReResolvedAfterTheTtl() {
        var name: String? = "Alice"
        val resolve: (String) -> String? = { name }
        assertEquals("Alice", cache.getOrResolve("+15551230001", resolve))
        name = "Alicia"
        now += 6_000L
        assertEquals("Alicia", cache.getOrResolve("+15551230001", resolve))
    }

    @Test
    fun invalidateForcesAFreshLookupAndForgetsADeletedContact() {
        var name: String? = "Alice"
        val resolve: (String) -> String? = { name }
        assertEquals("Alice", cache.getOrResolve("+15551230001", resolve))
        name = null
        cache.invalidate()
        assertNull(
            "a deleted contact must stop resolving to its old name immediately",
            cache.getOrResolve("+15551230001", resolve)
        )
    }

    @Test
    fun aMissingNameIsCachedTooSoTheProviderIsNotHammered() {
        var lookups = 0
        val resolve: (String) -> String? = { lookups++; null }
        assertNull(cache.getOrResolve("+15551230009", resolve))
        assertNull(cache.getOrResolve("+15551230009", resolve))
        assertEquals("an unsaved number must not query on every render", 1, lookups)
    }
}
