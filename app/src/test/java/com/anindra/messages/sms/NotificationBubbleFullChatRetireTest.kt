package com.anindra.messages.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Opening a thread full-screen must retire its floating bubble (#354 follow-up):
 * the live bubble still floats over the chat screen it now duplicates.
 */
class NotificationBubbleFullChatRetireTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun src(relative: String): String =
        File(main, "java/com/anindra/messages/$relative").readText()

    @Test
    fun bubbleHostTracksOnlyLiveConversations() {
        assertFalse(BubbleHost.holds(1L))
        BubbleHost.enter(1L)
        assertTrue(BubbleHost.holds(1L))
        assertFalse(BubbleHost.holds(2L))
        assertFalse(BubbleHost.holds(null))
        BubbleHost.leave(1L)
        assertFalse(BubbleHost.holds(1L))
    }

    @Test
    fun retiringRetakesTheNotificationFromTheBubbleHost() {
        val helper = src("sms/SmsSupport.kt")
        assertTrue("full-chat open has no retire path", helper.contains("retireBubbleForFullChat"))
        assertTrue(
            "the retire path must release the bubble host before cancelling",
            helper.contains("retireBubbleForFullChat(context: Context, conversationId: Long?)")
        )
        val retire = helper.substringAfter("retireBubbleForFullChat(context: Context, conversationId: Long?)")
            .substringBefore("\n    }")
            .plus("\n    }")
        assertTrue(
            "a notification owned by a bubble host is never cancelled",
            retire.contains("BubbleHost.holds(conversationId)")
        )
        assertTrue(
            "the retire path never releases the held conversation",
            retire.contains("BubbleHost.leave(conversationId)")
        )
        assertTrue(
            "the retire path never cancels the bubble's notification",
            retire.contains("nm.cancel(ids.first)")
        )
    }

    @Test
    fun openingTheListThreadRetiresItsBubbleInBothUis() {
        val activity = src("MainActivity.kt")
        Regex("""clearConversationNotification\(this@MainActivity, id\)\s*com\.anindra\.messages\.sms\.NotificationHelper\s+\.retireBubbleForFullChat\(this@MainActivity, id\)""")
            .findAll(activity)
            .let { assertTrue("the in-app open path skips the retire", it.count() >= 2) }
    }

    @Test
    fun theOrdinaryDismissalStillDefersToALiveBubble() {
        val helper = src("sms/SmsSupport.kt")
        assertTrue("a live bubble host loses its notification to the chat's mark-read",
            helper.contains("if (BubbleHost.holds(conversationId)) return"))
    }
}
