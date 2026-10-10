package com.anindra.messages.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Picking someone from the new-chat list opens *them*, not a group they are in.
 *
 * A group carries its primary contact's address, so "Alex" and the "Roadtrip"
 * thread can share a row. When Alex has no private thread of his own, the old
 * lookup fell back to "the only conversation with this address" -- which is the
 * group. Tapping Alex in the picker then dropped the user into Roadtrip.
 *
 * The fallback still resolves a spelling variant of a private chat, because
 * that genuinely is the same conversation. It just may no longer return a
 * group, so the second candidate is checked for having a single recipient.
 *
 * Pinned at the source level: this is a private function inside `Repository`
 * whose behaviour lives in how the two lookups are ordered.
 */
class NewChatSkipsGroupTest {
    private fun locate(path: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, path)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate $path")
    }

    private val repo: String
        get() = locate("app/src/main/java/com/anindra/messages/data/Repository.kt")

    private val lookup: String
        get() = repo.substringAfter("fun getOrCreateConversationBlocking(")
            .substringBefore("conversationForInbound")

    @Test
    fun `a group is never the result of starting a new chat`() {
        assertTrue(
            "the spelling-variant fallback must check the conversation is a 1:1",
            lookup.contains("recipientCountBlocking")
        )
        assertTrue(
            "a group must be rejected outright, or picking Alex opens Roadtrip",
            Regex("""recipientCountBlocking\(alt\)\s*<=\s*1""").containsMatchIn(lookup)
        )
    }

    @Test
    fun `the fallback still resolves a private chat under another spelling`() {
        // The variant case is the reason the fallback exists: national and E.164
        // spellings of one person are one conversation. Only the group case is
        // rejected.
        assertTrue(
            "the variant lookup must still be consulted",
            lookup.contains("findConversationForAddress(target)")
        )
    }

    @Test
    fun `the count of recipients is what tells a group from a private chat`() {
        assertTrue(
            "there must be a single definition of 'is this a 1:1'",
            Regex("""fun recipientCountBlocking\(conversationId: Long\): Int""")
                .containsMatchIn(repo)
        )
        assertTrue(
            "it must count conversation_recipients",
            Regex("""SELECT count\(\*\) FROM conversation_recipients WHERE conversation_id=\?"""")
                .containsMatchIn(repo)
        )
    }

    @Test
    fun `inbound routing still prefers a private chat over a group`() {
        // Creating a private thread for Alex must not make his next text land
        // in it by accident: an SMS names a sender, and the 1:1 is the older,
        // intended destination. If this ever flips, messages go missing from
        // Roadtrip.
        val inbound = repo.substringAfter("private fun conversationIdForAddressBlocking")
            .substringBefore("private fun")
        assertTrue(
            "the single-recipient thread must win the shared address",
            Regex("""conversation_recipients r[\s\S]{0,120}?\)=1 DESC""").containsMatchIn(inbound)
        )
    }
}