package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #284: the home search must not blank its results on every keystroke.
 *
 * The blank was a single frame — too fast for uiautomator to catch — so the
 * rule is pinned here instead, where it can be checked deterministically.
 */
class HeldMessageMatchesTest {

    @Test
    fun aNewQueryKeepsTheOutgoingIdsUntilItsOwnArrive() {
        val held = HeldMessageMatches()
        held.onQueryStarted("01")
        held.onResultArrived("01", setOf(1L, 2L, 3L))
        assertEquals(setOf(1L, 2L, 3L), held.current())

        // The user typed another character. The new query is in flight, and the
        // list must not empty in the meantime — that was the flicker.
        held.onQueryStarted("010")
        assertEquals(setOf(1L, 2L, 3L), held.current())
        assertFalse(held.isSettledFor("010"))

        held.onResultArrived("010", setOf(2L))
        assertEquals(setOf(2L), held.current())
        assertTrue(held.isSettledFor("010"))
    }

    @Test
    fun theFirstKeystrokeHasNothingToHold() {
        val held = HeldMessageMatches()
        assertEquals(emptySet<Long>(), held.current())
        held.onQueryStarted("0")
        assertEquals(emptySet<Long>(), held.current())
    }

    @Test
    fun clearingTheQueryDropsTheIdsAtOnce() {
        val held = HeldMessageMatches()
        held.onQueryStarted("abc")
        held.onResultArrived("abc", setOf(1L))
        assertEquals(setOf(1L), held.current())

        // Leaving search must not leave stale ids filtering the list.
        held.onQueryStarted("")
        assertEquals(emptySet<Long>(), held.current())
    }

    @Test
    fun aQueryWithNoHitsSettlesOnEmpty() {
        val held = HeldMessageMatches()
        held.onQueryStarted("abc")
        held.onResultArrived("abc", setOf(1L))
        held.onQueryStarted("abcd")
        assertEquals(setOf(1L), held.current())

        // A genuinely empty result is a real answer and must replace the
        // outgoing ids, otherwise a narrowed query would keep showing rows.
        held.onResultArrived("abcd", emptySet())
        assertEquals(emptySet<Long>(), held.current())
    }
}