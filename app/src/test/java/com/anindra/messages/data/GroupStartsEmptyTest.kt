package com.anindra.messages.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A group started from a 1:1 must be its own empty thread.
 *
 * Adding people used to mutate the conversation in place, so the whole private
 * history moved into the group. The new group is a separate row, which means
 * two conversations share an address -- the `UNIQUE` constraint that used to
 * guarantee one-row-per-contact had to go, and the two sync passes that treated
 * a message-less row or a shared address as debris had to stop doing so.
 *
 * Each of those is easy to reintroduce silently, so they are pinned here at the
 * source level: they live inside `Repository`'s sync, which needs a live
 * ContentResolver to run.
 */
class GroupStartsEmptyTest {
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

    private val createStatement: String
        get() = repo.substringAfter("override fun onCreate(db: SQLiteDatabase)")
            .substringBefore("createdDatabase")

    @Test
    fun `address is not unique`() {
        assertTrue(
            "could not find the fresh-install CREATE TABLE for conversations",
            createStatement.contains("CREATE TABLE conversations(")
        )
        assertFalse(
            "A group shares its primary contact's address, so UNIQUE would make it uncreatable",
            createStatement.contains("address TEXT NOT NULL UNIQUE")
        )
    }

    @Test
    fun `every existing database is migrated off the constraint`() {
        val src = repo
        assertTrue(
            "SQLite cannot drop a column constraint in place, so the table must be rebuilt",
            src.contains("ALTER TABLE conversations RENAME TO conversations_preunique")
        )
        val migration = src.substringAfter("private fun dropConversationAddressUnique(db: SQLiteDatabase)")
            .substringBefore("override fun")
        // The rebuild has to carry every column across, or an upgrade silently
        // drops data -- which no test at runtime would notice.
        listOf(
            "id", "address", "name", "snippet", "timestamp", "unread_count",
            "last_is_me", "archived", "blocked", "blocked_at", "pinned",
            "draft", "draft_date", "deleted_at", "deleted_reason", "group_title"
        ).forEach { column ->
            assertTrue("the rebuilt table is missing $column", migration.contains(column))
        }
        assertTrue(
            "the copy must carry the rows over, not just recreate an empty table",
            migration.contains("FROM conversations_preunique")
        )
        assertTrue(
            "the rebuilt table must not reintroduce the constraint",
            !migration.contains("address TEXT NOT NULL UNIQUE")
        )
    }

    @Test
    fun `an empty group is not swept away as a leftover`() {
        val body = repo.substringAfter("private fun removeEmptiedConversations()")
            .substringBefore("\n    }")
        assertTrue(
            "a group is created empty on purpose, so this would erase it on the next sync",
            body.contains("HAVING count(*)>1")
        )
    }

    @Test
    fun `a group is never merged into the chat it shares an address with`() {
        val body = repo.substringAfter("private fun mergeSplitConversations()")
            .substringBefore("private fun syncFromSystem")
        assertTrue(
            "the merge pass would fold the group in and delete it",
            body.contains("HAVING count(*)>1")
        )
        assertTrue(
            "a group must be excluded as a merge source as well as a target",
            body.contains("b.first in groups")
        )
    }

    @Test
    fun `a shared address resolves to the private chat`() {
        // A new chat and an inbound SMS must both pick the 1:1 over the group
        // that happens to share the contact, or the two threads steal each
        // other's messages.
        assertTrue(
            "the inbound lookup must prefer the single-recipient thread",
            Regex(
                "ORDER BY \\(SELECT count\\(\\*\\) FROM conversation_recipients r\\s*" +
                    "WHERE r\\.conversation_id=c\\.id\\)=1 DESC, c\\.id ASC"
            ).containsMatchIn(repo),
        )
        // The new-chat lookup additionally refuses a group outright: ordering
        // groups last is not enough, because LIMIT 1 still takes one when the
        // person has no private thread of their own.
        val open = repo.substringAfter("fun getOrCreateConversationBlocking(")
            .substringBefore("\n    fun ")
        assertTrue(
            "starting a new chat must be able to exclude groups",
            open.contains("privateOnly: Boolean = false") &&
                open.contains("if (privateOnly)")
        )
        assertTrue(
            "the picker path must ask for that behaviour",
            Regex("""fun getOrCreateConversation\([\s\S]{0,120}?privateOnly = true""")
                .containsMatchIn(repo),
        )
    }
}