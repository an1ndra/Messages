package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHistoryTest {

    private fun line(text: String, t: Long = 0L, me: Boolean = false) =
        NotificationLine(text, t, me)

    @Test
    fun keepsOnlyTheNewestLines() {
        val many = (1..9).map { line("m$it", it.toLong()) }
        val kept = NotificationHistory.window(many)
        assertEquals(NotificationHistory.MAX_LINES, kept.size)
        assertEquals(listOf("m5", "m6", "m7", "m8", "m9"), kept.map { it.text })
    }

    @Test
    fun staysOldestFirst() {
        val out = NotificationHistory.window((1..3).map { line("m$it", it.toLong()) })
        assertEquals(listOf("m1", "m2", "m3"), out.map { it.text })
        assertEquals(listOf(1L, 2L, 3L), out.map { it.timestamp })
    }

    @Test
    fun dropsBlankLines() {
        val out = NotificationHistory.window(listOf(line(""), line("real"), line("   ")))
        assertEquals(listOf("real"), out.map { it.text })
    }

    @Test
    fun aSingleMessageIsPassedThrough() {
        val out = NotificationHistory.window(listOf(line("only")))
        assertEquals(1, out.size)
        assertEquals("only", out.first().text)
    }

    @Test
    fun handlesNoMessages() {
        assertTrue(NotificationHistory.window(emptyList()).isEmpty())
    }

    @Test
    fun takeCountIsBoundedByWhatIsUnread() {
        assertEquals(2, NotificationHistory.takeCount(unread = 2, maxLines = 5))
        assertEquals(3, NotificationHistory.takeCount(unread = 3, maxLines = 5))
    }

    @Test
    fun takeCountNeverExceedsTheCap() {
        assertEquals(5, NotificationHistory.takeCount(unread = 40, maxLines = 5))
        assertEquals(5, NotificationHistory.takeCount(unread = 5, maxLines = 5))
    }

    @Test
    fun takeCountAlwaysCarriesTheMessageThatTriggeredThePost() {
        // unread can already be 0 if the chat is on screen, but the notification
        // still has to show the line that caused it.
        assertEquals(1, NotificationHistory.takeCount(unread = 0, maxLines = 5))
        assertEquals(1, NotificationHistory.takeCount(unread = -1, maxLines = 5))
    }

    @Test
    fun summaryCountsUpFromTwo() {
        assertEquals("A", NotificationHistory.summary(1, "A"))
        assertEquals("3 new messages", NotificationHistory.summary(3, "A"))
        assertEquals("A", NotificationHistory.summary(0, "A"))
    }
}
