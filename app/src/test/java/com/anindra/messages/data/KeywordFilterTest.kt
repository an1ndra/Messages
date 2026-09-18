package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordFilterTest {

    @Test
    fun emptyListBlocksNothing() {
        assertFalse(KeywordFilter.isBlocked("anything at all", emptySet()))
    }

    @Test
    fun matchesSubstringCaseInsensitively() {
        val keywords = setOf("OTP", "winner")
        assertTrue(KeywordFilter.isBlocked("Your OTP is 1234", keywords))
        assertTrue(KeywordFilter.isBlocked("you are a WINNER!", keywords))
        assertTrue(KeywordFilter.isBlocked("winner", keywords))
    }

    @Test
    fun nonMatchingBodyIsAllowed() {
        assertFalse(KeywordFilter.isBlocked("See you at 8", setOf("OTP", "winner")))
    }

    @Test
    fun blankKeywordsAreIgnored() {
        assertFalse(KeywordFilter.isBlocked("hello world", setOf("", "   ")))
        assertTrue(KeywordFilter.isBlocked("hello world", setOf("", "world")))
    }
}
