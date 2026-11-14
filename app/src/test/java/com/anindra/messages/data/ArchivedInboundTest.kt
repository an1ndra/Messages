package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Issue #468: a message arriving in an archived conversation brings it back to
 * the inbox, the way Google Messages does.
 *
 * Archiving is a filing decision, and the app never undid it — the archive flag
 * was only ever cleared when a *trashed* thread came back (#405), so a chat
 * filed away stayed there forever.
 *
 * These assertions scan the source because the rule lives in SQL inside the
 * repository: there is no pure function left to call, and the two ingest paths
 * (a live SMS, and one imported from the provider on the next launch) have to
 * agree or the behaviour depends on whether the app happened to be running.
 */
class ArchivedInboundTest {

    private val repository: String by lazy {
        val candidates = listOf(
            "app/src/main/java/com/anindra/messages/data/Repository.kt",
            "../app/src/main/java/com/anindra/messages/data/Repository.kt",
            "../../app/src/main/java/com/anindra/messages/data/Repository.kt"
        )
        candidates.firstNotNullOfOrNull { File(it).takeIf { f -> f.exists() }?.readText() }
            ?: error("Repository.kt not found")
    }

    private fun body(start: String, end: String): String =
        repository.substringAfter(start).substringBefore(end)

    @Test
    fun theUnarchiveRuleExists() {
        assertTrue(
            "archived has to be cleared against the conversation id",
            Regex("""UPDATE conversations SET archived=0 WHERE id=\? AND archived=1""")
                .containsMatchIn(repository)
        )
    }

    @Test
    fun aLiveTextUnarchivesTheConversation() {
        val receive = body("fun receiveMessage(", "fun receiveBlockedMessage(")
        assertTrue(
            "receiveMessage must unarchive before it bumps the snippet",
            receive.contains("unarchiveForInbound(convoId)")
        )
    }

    @Test
    fun anImportedTextUnarchivesItToo() {
        val sync = body("fun syncFromSystem()", "private fun importProviderMmsOnce()")
        assertTrue(
            "the provider import has to apply the same rule as a live message",
            sync.contains("unarchiveForInbound(cid)")
        )
        assertTrue(
            "only an inbound provider row may unarchive",
            Regex("""MESSAGE_TYPE_INBOX ->[\s\S]{0,4000}unarchiveForInbound\(cid\)""")
                .containsMatchIn(sync)
        )
    }

    @Test
    fun anInboundPictureUnarchivesIt() {
        val mms = body("private fun importProviderMmsOnce()", "fun importDownloadedMms()")
        assertTrue(
            "an arriving MMS is a new message as well",
            mms.contains("if (!message.isMe) unarchiveForInbound(cid)")
        )
    }

    @Test
    fun filteredSendersCannotUnarchiveAnything() {
        listOf(
            "fun receiveBlockedMessage(" to "fun receiveSpamMessage(",
            "fun receiveSpamMessage(" to "fun setConversationBlocked"
        ).forEach { (start, end) ->
            val section = body(start, end)
            assertFalse(
                "$start must not pull a thread out of the archive",
                section.contains("unarchiveForInbound")
            )
        }
    }
}
