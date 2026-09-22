package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockedNumbersTest {

    @Test
    fun emptyListShowsNone() {
        assertEquals("None", blockedNumbersSubtitle("None", "%d blocked", 0))
    }

    @Test
    fun countIsFormatted() {
        assertEquals("1 blocked", blockedNumbersSubtitle("None", "%d blocked", 1))
        assertEquals("3 blocked", blockedNumbersSubtitle("None", "%d blocked", 3))
    }
}
