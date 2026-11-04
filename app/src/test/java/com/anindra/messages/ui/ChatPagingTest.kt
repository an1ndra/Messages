package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chat history paging (issue #406). The old pager stopped growing at an
 * AUTO_CAP of 400 rows, so a long thread simply ended mid-history, and a search
 * jump walked toward its hit in fixed 200-row steps behind a settling delay.
 */
class ChatPagingTest {

    @Test
    fun scrollUpKeepsGrowingPastTheOldFourHundredRowCap() {
        var limit = 40
        val total = 1_000
        repeat(12) { limit = grownPageLimit(limit, total) }
        assertEquals(40 + 12 * AUTO_CHUNK, limit)
        assertTrue("must keep loading past the old 400-row cap", limit > 400)
        assertTrue(hasEarlierMessages(total, limit))
    }

    @Test
    fun growthIsOneChunkAtATime() {
        assertEquals(80, grownPageLimit(40, 1_000))
        assertEquals(40 + AUTO_CHUNK, grownPageLimit(40, 1_000))
    }

    @Test
    fun growthStopsAtTheTotal() {
        assertEquals(1_000, grownPageLimit(980, 1_000))
        assertEquals(1_000, grownPageLimit(1_000, 1_000))
    }

    @Test
    fun growthIsMonotonic() {
        var limit = 40
        repeat(5) {
            val next = grownPageLimit(limit, 1_000)
            assertTrue("$next must not shrink from $limit", next >= limit)
            limit = next
        }
    }

    @Test
    fun aSearchJumpReachesTheHitInOnePage() {
        // A hit 1,500 rows back used to take eight 200-row steps, each behind
        // the scroll-up settling delay.
        val wanted = searchPageLimit(newerCount = 1_500, total = 2_000)
        assertTrue(wanted > 1_500)
        assertEquals(1_500 + SEARCH_CONTEXT_ROWS, wanted)
    }

    @Test
    fun aSearchJumpNeverExceedsTheThread() {
        assertEquals(90, searchPageLimit(newerCount = 80, total = 90))
        assertEquals(0, searchPageLimit(newerCount = 0, total = 0))
    }

    @Test
    fun searchContextKeepsSomeOlderHistoryAboveTheHit() {
        val wanted = searchPageLimit(newerCount = 300, total = 1_000)
        assertTrue(wanted - 300 >= SEARCH_CONTEXT_ROWS)
    }

    @Test
    fun earlierMessagesExistOnlyBelowTheLoadedWindow() {
        assertTrue(hasEarlierMessages(totalCount = 401, pageLimit = 400))
        assertFalse(hasEarlierMessages(totalCount = 400, pageLimit = 400))
        assertFalse(hasEarlierMessages(totalCount = 0, pageLimit = 0))
    }
}
