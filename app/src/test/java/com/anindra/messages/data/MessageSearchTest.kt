package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home-list search handoff: which message a query points at and the ranges
 * the bubble paints, kept pure so the scroll target and the styled spans cannot
 * disagree.
 */
class MessageSearchTest {

    private fun msg(id: Long, body: String) =
        Message(id = id, conversationId = 1L, body = body, timestamp = id, isMe = false, status = "received")

    @Test
    fun matchesIsCaseInsensitive() {
        assertTrue(MessageSearch.matches("Hello ABC world", "abc"))
        assertTrue(MessageSearch.matches("abc", "ABC"))
        assertFalse(MessageSearch.matches("Hello world", "abc"))
    }

    @Test
    fun blankQueryMatchesNothing() {
        assertFalse(MessageSearch.matches("anything", ""))
        assertFalse(MessageSearch.matches("anything", "   "))
        assertTrue(MessageSearch.ranges("anything", "").isEmpty())
    }

    @Test
    fun rangesCoverEveryOccurrence() {
        assertEquals(listOf(0 until 3, 7 until 10), MessageSearch.ranges("abc xx abc", "abc"))
        assertEquals(listOf(4 until 7), MessageSearch.ranges("Say ABC now", "abc"))
    }

    @Test
    fun visibleMatchesNeverSeeLockedBodiesOrHiddenLinks() {
        val redactor: (String) -> String = { body -> body.replace(Regex("https?://\\S+"), "") }
        val body = "pay https://secret.example/now please"
        assertTrue(
            MessageSearch.matchesVisible(
                body, "please", locked = false, hideLinks = true, redactor = redactor
            )
        )
        assertTrue(
            MessageSearch.matchesVisible(
                body, "secret.example", locked = false, hideLinks = false, redactor = redactor
            )
        )
        assertFalse(
            MessageSearch.matchesVisible(
                body, "secret.example", locked = false, hideLinks = true, redactor = redactor
            )
        )
        assertFalse(
            MessageSearch.matchesVisible(
                body, "please", locked = true, hideLinks = false, redactor = redactor
            )
        )
    }

    @Test
    fun focusedIdIsTheNewestMatch() {
        val messages = listOf(
            msg(1, "no hit here"),
            msg(2, "an abc early"),
            msg(3, "nothing"),
            msg(4, "the abc late")
        )
        assertEquals(4L, MessageSearch.focusedId(messages, "abc"))
    }

    @Test
    fun focusedIdIsNullWhenNothingMatches() {
        val messages = listOf(msg(1, "no hit"), msg(2, "still none"))
        assertNull(MessageSearch.focusedId(messages, "abc"))
    }

    @Test
    fun stepClampsToTheMatchBounds() {
        // Previous from the newest goes older; next from the oldest goes newer;
        // both stop at the ends.
        assertEquals(1, MessageSearch.step(2, -1, 3))
        assertEquals(2, MessageSearch.step(1, 1, 3))
        assertEquals(0, MessageSearch.step(0, -1, 3))
        assertEquals(2, MessageSearch.step(2, 1, 3))
    }

    @Test
    fun stepIsNoMatchWhenThereAreNone() {
        assertEquals(-1, MessageSearch.step(-1, 1, 0))
        assertEquals(-1, MessageSearch.step(0, -1, 0))
    }
}
