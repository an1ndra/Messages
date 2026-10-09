package com.anindra.messages.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One group send must produce exactly one message here.
 *
 * Group text goes out as one SMS per recipient, so a single tap leaves N rows
 * in the Sent box. `messages.sys_id` can record only one, and sync adopted the
 * first onto the local row and then imported the rest as brand-new messages —
 * so every group text appeared N times.
 *
 * `message_provider_ids` is the fix: it holds the full set, while
 * `messages.sys_id` stays as the primary row so every other caller is
 * unaffected. Pinned at the source level because the dedup lives inside
 * `Repository`'s sync, which needs a live ContentResolver to run.
 */
class GroupSendProviderIdTest {
    private fun locate(path: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
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

    @Test
    fun theMappingTableExistsAndIsMigrated() {
        assertTrue(
            "message_provider_ids must be created",
            repo.contains("CREATE TABLE IF NOT EXISTS message_provider_ids("),
        )
        assertTrue(
            "existing databases must be migrated, not only fresh ones",
            Regex("if \\(oldVersion < 25\\) \\{[\\s\\S]{0,600}?createMessageProviderIds\\(db\\)")
                .containsMatchIn(repo),
        )
        assertTrue("the schema version must have been bumped", repo.contains("DB_VERSION = 25"))
        assertTrue(
            "every already-linked id must be backfilled",
            Regex("""INSERT OR IGNORE INTO message_provider_ids[\s\S]{0,200}?SELECT id, transport, sys_id FROM messages WHERE sys_id>0""")
                .containsMatchIn(repo),
        )
    }

    @Test
    fun theSeenSetComesFromTheMappingTableNotMessagesSysId() {
        // Reading messages.sys_id would miss the extra rows and re-import them
        // on every sync — the duplicate would come back.
        assertTrue(
            "the 'already imported' set must be read from message_provider_ids",
            Regex("SELECT sys_id FROM message_provider_ids WHERE transport=\\? AND sys_id IN")
                .containsMatchIn(repo),
        )
    }

    @Test
    fun anUnclaimedProviderRowJoinsTheSameMessageInsteadOfBecomingANewOne() {
        assertTrue(
            "a provider row with no sys_id=0 row to adopt must look for the message " +
                "it belongs to within a tight window",
            Regex("""ABS\(timestamp-\?\) < 120000""").containsMatchIn(repo),
        )
        assertTrue(
            "the join must record the extra id on the existing message",
            Regex("""INSERT OR IGNORE INTO message_provider_ids\(message_id, transport, sys_id\)""")
                .containsMatchIn(repo),
        )
        assertTrue(
            "messages.sys_id must keep only the primary row",
            repo.contains("UPDATE messages SET sys_id=? WHERE id=? AND sys_id=0"),
        )
    }

    @Test
    fun deletingAConversationPurgesEveryProviderRowItOwns() {
        // Leaving the extras behind would strand them in the Sent box, to be
        // re-imported later as a one-to-one thread.
        val purge = repo.substringAfter("private fun purgeProviderMessages(")
            .substringBefore("\n    }")
        assertTrue(
            "purge must collect ids from message_provider_ids",
            purge.contains("FROM message_provider_ids p"),
        )
    }

    @Test
    fun groupTextStillFansOutOneSmsPerMember() {
        // The fix is about bookkeeping, not about changing the wire format:
        // group text stays N independent SMS, which is what Google Messages
        // does and what makes each member's copy look like a private text.
        val vm = locate("app/src/main/java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "dispatchText must still send once per recipient",
            vm.contains("recipients.map { address ->"),
        )
    }
}