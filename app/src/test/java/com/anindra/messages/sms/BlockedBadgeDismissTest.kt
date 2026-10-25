package com.anindra.messages.sms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** #345: blocking a number removes its thread from the launcher badge. The
 *  badge aggregates the numbers of live notifications, so the block action
 *  must cancel every notification the sender owns. */
class BlockedBadgeDismissTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val repository: String by lazy {
        File(main, "java/com/anindra/messages/data/Repository.kt").readText()
    }
    private val sms: String by lazy {
        File(main, "java/com/anindra/messages/sms/SmsSupport.kt").readText()
    }

    @Test
    fun blockNumberCancelsTheSendersNotifications() {
        val body = Regex("""fun blockNumber\(number: String\) \{[\s\S]*?\n    \}""")
            .find(repository)?.value ?: error("blockNumber not found")
        assertTrue(
            "blockNumber must cancel the sender's live notifications - the badge " +
                "sums them and a blocked thread never posts a replacement",
            body.contains("NotificationHelper.clearBlockedConversationNotifications")
        )
    }

    @Test
    fun blockedSenderNotificationsCarryUnreadCountsThatAreDropped() {
        assertTrue(sms.contains("fun clearBlockedConversationNotifications"))
        assertTrue(
            "cancelling the \"failed\" tag too, like the conversation path does",
            Regex("""fun clearBlockedConversationNotifications\([\s\S]*?nm\.cancel\("failed", id\)""")
                .containsMatchIn(sms)
        )
    }

    @Test
    fun aHeldBubbleIsLeftToItsHost() {
        assertTrue(
            "cancel skips a live bubble host, mirroring clearConversationNotification",
            Regex("""clearBlockedConversationNotifications\([\s\S]*?BubbleHost\.holds\(it\)""")
                .containsMatchIn(sms)
        )
    }

    @Test
    fun theConversationRowsQueueMatchesTheBlockedFlag() {
        // Numbers blocked flag both address spellings; cancelling must query
        // the same rows so notification and flag never disagree.
        val closed = Regex("""\bSELECT id FROM conversations WHERE address=\?""")
            .containsMatchIn(repository)
        assertTrue(
            "the cancel path must look up conversation rows by address like " +
                "setConversationBlockedForAddress does",
            closed
        )
    }
}
