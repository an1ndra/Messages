package com.anindra.messages.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #449: a deleted conversation came back after a force-close or reboot.
 *
 * The sync treats every provider row missing from `message_provider_ids` as
 * never imported. Received SMS, sent SMS and imported MMS wrote only
 * `messages.sys_id`, so after a restart their rows read as pending: the
 * re-insert failed on the unique index and the address still went through
 * [Repository.getOrCreateConversationBlocking], which un-trashes a deleted
 * thread. The provider purge had the same blind spot, so a permanent delete
 * left the row behind to be imported again.
 *
 * Pinned at the source level because both paths need a live ContentResolver.
 */
class DeletedConversationRestartTest {

    private fun locate(path: String): String {
        var dir: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (dir != null) {
            val candidate = File(dir, path)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate $path")
    }

    private val repo: String by lazy {
        locate("app/src/main/java/com/anindra/messages/data/Repository.kt")
    }

    private val sync: String by lazy {
        repo.substringAfter("fun syncFromSystem()").substringBefore("\n    /** ")
    }

    private val purge: String by lazy {
        repo.substringAfter("private fun purgeProviderMessages(").substringBefore("\n    }")
    }

    @Test
    fun everyStoredProviderRowIsLinkedBeforeThePendingSetIsRead() {
        assertTrue(
            "sync must record the provider rows it already stores",
            sync.contains("linkStoredProviderIds()"),
        )
        assertTrue(
            "the backfill must cover every message that came from the provider",
            Regex(
                """INSERT OR IGNORE INTO message_provider_ids\(message_id, transport, sys_id\)[\s\S]{0,200}?SELECT id, transport, sys_id FROM messages WHERE sys_id>0"""
            ).containsMatchIn(repo),
        )
        val linked = sync.indexOf("linkStoredProviderIds()")
        val seen = sync.indexOf("SELECT sys_id FROM message_provider_ids")
        assertTrue("the seen set must still come from the linkage table", seen > 0)
        assertTrue(
            "linking must happen before the pending set is computed, or the " +
                "restart still un-trashes the conversation",
            linked in 0 until seen,
        )
    }

    @Test
    fun thePurgeSeesRowsThatWereNeverLinked() {
        assertTrue(
            "purge must collect ids from message_provider_ids",
            purge.contains("FROM message_provider_ids p"),
        )
        assertTrue(
            "purge must also collect messages.sys_id, the only place an " +
                "unlinked row was recorded",
            Regex("""SELECT transport, sys_id FROM messages[\s\S]{0,120}?sys_id>0""")
                .containsMatchIn(purge),
        )
    }

    @Test
    fun anImportedMmsLinksItsProviderRow() {
        val import = repo.substringAfter("private fun importProviderMmsOnce(")
            .substringBefore("\n    private fun ")
        assertTrue(
            "an MMS import must link the provider row it consumed, or the " +
                "purge can never remove it",
            Regex(
                """INSERT OR IGNORE INTO message_provider_ids\(message_id, transport, sys_id\)[\s\S]{0,200}?transport=\? AND sys_id=\?"""
            ).containsMatchIn(import),
        )
    }

    @Test
    fun hardDeleteDropsTheLinkageRowsToo() {
        val delete = repo.substringAfter("fun deleteConversationSuspend(")
            .substringBefore("\n    }")
        assertTrue(
            "a permanent delete must not leave linkage rows pointing at " +
                "messages that no longer exist",
            delete.contains("DELETE FROM message_provider_ids"),
        )
        val empty = repo.substringAfter("fun emptyTrashSuspend(")
            .substringBefore("\n    }")
        assertTrue(
            "emptying the trash must clean the same linkage rows",
            empty.contains("DELETE FROM message_provider_ids"),
        )
    }
}
