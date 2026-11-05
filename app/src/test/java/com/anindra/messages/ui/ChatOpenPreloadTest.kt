package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reported twice: "I have so many messages ... when I open chat I see chat
 * flickering every time I open chat".
 *
 * The cause was not the message rendering: opening a 300-message chat loaded
 * older history in 40-row chunks (7 loads, 8 bottom snaps in 2.5s) while the list
 * was parked at the bottom. Each prepend shifts every visible row for a frame and
 * the follow-bottom snap pulls it back, so the thread visibly flashed once per
 * chunk. Older history now loads only when the reader reaches the top.
 */
class ChatOpenPreloadTest {

    @Test
    fun anEmptyChatDoesNotLoadEarlier() {
        assertFalse(shouldLoadEarlier(totalItems = 0, firstVisibleIndex = 0))
    }

    @Test
    fun theBottomOfALoadedWindowDoesNotLoadEarlier() {
        // Open state: 40 rows loaded, the newest ones on screen.
        assertFalse(shouldLoadEarlier(totalItems = 40, firstVisibleIndex = 30))
        assertFalse(shouldLoadEarlier(totalItems = 300, firstVisibleIndex = 280))
        assertFalse(shouldLoadEarlier(totalItems = 40, firstVisibleIndex = 12))
    }

    @Test
    fun reachingTheTopLoadsEarlier() {
        assertTrue(shouldLoadEarlier(totalItems = 40, firstVisibleIndex = 0))
        assertTrue(shouldLoadEarlier(totalItems = 40, firstVisibleIndex = 3))
    }

    @Test
    fun theSlackStopsJustAboveTheFirstMessage() {
        assertFalse(shouldLoadEarlier(totalItems = 40, firstVisibleIndex = 4))
    }
}
