package com.anindra.messages.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retrying or forwarding one picture of a multi-picture message has to act on
 * the whole message, not on the row that happened to be tapped (#364).
 *
 * The rows exist one per picture, sharing the provider id of the single MMS they
 * were sent as. Re-sending only the tapped row would deliver one picture of
 * three, and forwarding only it would leave the rest behind. Pinned at the
 * source level because the behaviour sits in `MainActivity`, which needs a live
 * Activity and ContentResolver to run.
 */
class AlbumRetryForwardTest {

    private val activity: String by lazy {
        locate("app/src/main/java/com/anindra/messages/MainActivity.kt")
    }
    private val chat: String by lazy {
        locate("app/src/main/java/com/anindra/messages/ui/ChatScreen.kt")
    }
    private val repo: String by lazy {
        locate("app/src/main/java/com/anindra/messages/data/Repository.kt")
    }

    @Test
    fun retryingAPictureResendsEveryPictureOfItsMessage() {
        val retry = section(activity, "fun retryMessage(")
        assertTrue(
            "retry must resolve the album from the provider id",
            retry.contains("repo.messagesBySysId(msg.sysId)"),
        )
        assertTrue(
            "every row must be linked to the resend",
            retry.contains("rows.map { it.id }"),
        )
        assertTrue(
            "every picture must travel in the one PDU",
            retry.contains("rows.map { Uri.parse(it.mediaUri) }"),
        )
    }

    @Test
    fun forwardingSelectionNamesEachMessageOnce() {
        val forward = section(activity, "fun forwardMessages(")
        assertTrue(
            "a selection names every row of an album, so repeats must collapse",
            forward.contains("msg.sysId > 0 && !seen.add(msg.sysId)"),
        )
        assertTrue(
            "the one-argument entry point must go through the same grouping",
            section(activity, "fun forwardMessage(").contains("forwardMessages(listOf(messageId)"),
        )
        val unit = section(activity, "private suspend fun forwardOne(")
        assertTrue(
            "the album is resolved from the provider id",
            unit.contains("repo.messagesBySysId(msg.sysId)"),
        )
        assertTrue(
            "the forwarded album is stored as several rows of one message",
            unit.contains("repo.sendMediaAlbum(targetConversationId, types, uris, caption)"),
        )
        assertFalse(
            "the old one-row forward must be gone",
            activity.contains("repo.sendMedia(targetConversationId, plan.mediaType"),
        )
        assertTrue(
            "the chat hands the whole selection over in one call",
            chat.contains("vm.forwardMessages(targets, targetId)"),
        )
    }

    @Test
    fun aRefusedAlbumHandoffFailsEveryRow() {
        // A refusal happens before the provider links the rows, so the sys_id
        // cascade cannot reach the siblings; the hand-off itself has to mark
        // the whole album or the pictures sit at "sending" forever.
        val handOff = section(activity, "private suspend fun handOff(")
        assertTrue(
            "every handed-off row must be marked failed",
            handOff.contains("rowIds.forEach { repo.markMessageStatusSuspend(it, \"failed\") }"),
        )
        val unit = section(activity, "private suspend fun forwardOne(")
        assertTrue(
            "the forwarded album hands off all of its rows",
            unit.contains("handOff(stored.map { it.id }"),
        )
        assertTrue(
            "the hidden-links setting applies to the album's caption too",
            unit.contains("val caption = if (settings.hideLinks) hideUrls(rawCaption) else rawCaption"),
        )
    }

    @Test
    fun anAlbumReadsBackInTheOrderItWasStored() {
        val bySysId = section(repo, "fun messagesBySysId(")
        assertTrue(
            "the album rows must be returned oldest first",
            bySysId.contains("WHERE sys_id=? ORDER BY id ASC"),
        )
        assertTrue(
            "a message with no provider row is not an album",
            bySysId.contains("if (sysId <= 0) return@runOnIoAsync emptyList()"),
        )
    }

    /** From [anchor] to the next top-level `    }`, enough for an assertion. */
    private fun section(source: String, anchor: String): String {
        val start = source.indexOf(anchor)
        check(start >= 0) { "could not find $anchor" }
        val end = source.indexOf("\n    }\n", start)
        return source.substring(start, if (end < 0) source.length else end)
    }

    private fun locate(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate $relative")
    }
}
