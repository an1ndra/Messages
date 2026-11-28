package com.anindra.messages.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reported on Android 16 (#524): one person's private texts popped the wrong
 * chat's notification, and it survived them deleting the messages inside it.
 *
 * A group started from a 1:1 carries that contact's address, so two rows match
 * the same number. Filing a message already knew a private chat outranks the
 * group, but the notifier looked the number up a second time with its own query
 * and took whichever row came first — the older group. The message was in the
 * right chat; only the notification was on another one.
 *
 * Both halves are pinned here, because either one coming back reproduces it.
 */
class NotificationConversationLookupTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val repo get() = read("src/main/java/com/anindra/messages/data/Repository.kt")
    private val receiver get() = read("src/main/java/com/anindra/messages/sms/SmsReceiver.kt")

    @Test
    fun theReceivePathNotifiesOnTheChatItFiledTheMessageInto() {
        assertTrue(
            "the notification must ride on the conversation receiveMessage returned",
            receiver.contains("val convoId = repo.receiveMessage(")
        )
        assertTrue(
            "and be posted on it, not on a second lookup of the sender",
            receiver.contains("NotificationHelper.show(context, address, body, conversationId = convoId)")
        )
    }

    @Test
    fun theAddressLookupHasOneRule() {
        val body = repo
            .substringAfter("fun conversationIdForAddress(address: String): Long?")
            .substringBefore("\n    fun ")
        assertTrue(
            "it must delegate to the lookup that settles the tie",
            body.contains("conversationIdForAddressBlocking(address)")
        )
        assertFalse(
            "a query of its own is exactly how the two disagreed (#524)",
            body.contains("SELECT id FROM conversations")
        )
    }

    @Test
    fun theTieGoesToThePrivateChat() {
        val rule = repo
            .substringAfter("private fun conversationIdForAddressBlocking(address: String): Long?")
            .substringBefore("\n    }")
        assertTrue(
            "one recipient wins, so a text from that person means their own chat",
            rule.contains("ORDER BY (SELECT count(*) FROM conversation_recipients r")
        )
        assertTrue(
            "and the older row must not win just by being older",
            rule.contains(")=1 DESC, c.id ASC")
        )
    }
}
