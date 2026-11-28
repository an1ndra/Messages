package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** #405: a chat that was archived and then trashed must come back to the Inbox,
 *  not to the Archived folder. Restoring only clears `deleted_at`, so the stale
 *  `archived` flag kept it out of the Inbox (and its search) while messages,
 *  notifications and sends kept working. */
class TrashRestoreClearsArchiveTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val repository: String by lazy {
        File(main, "java/com/anindra/messages/data/Repository.kt").readText()
    }

    private val unArchives = Regex("""archived\s*=\s*0""")

    @Test
    fun restoringFromTheTrashAlsoLeavesTheArchive() {
        val body = Regex("""fun restoreFromTrashSuspend\(conversationId: Long\) \{[\s\S]*?\n    \}""")
            .find(repository)?.value ?: error("restoreFromTrashSuspend not found")
        assertTrue(
            "explicit Restore must clear archived too, or the chat returns to " +
                "the Archived folder where the Inbox never shows it",
            body.contains("deleted_at=0") && unArchives.containsMatchIn(body)
        )
    }

    @Test
    fun anInboundMessageDoesNotReviveTheTrashedChat() {
        val body = Regex(
            """fun getOrCreateConversationBlocking\([\s\S]*?\n    \}"""
        ).find(repository)?.value ?: error("getOrCreateConversationBlocking not found")
        assertTrue(
            "normal inbound messages must only look up active conversations",
            body.contains("findConversationForAddress(target, activeOnly = !mayReuseTrashed)")
        )
        assertTrue(
            "only blocked traffic may reuse a trashed conversation",
            body.contains("InboundIngest.mayReuseTrashedConversation(kind)")
        )
        assertFalse(
            "an inbound message must not clear deleted_at and resurrect deleted history",
            body.contains("put(\"deleted_at\", 0)")
        )
    }

    @Test
    fun importingABackupUnTrashesEverythingIntoTheInbox() {
        val statements = Regex("""UPDATE conversations SET[^"]*WHERE deleted_at>0""")
            .findAll(repository).map { it.value }.toList()
        assertTrue("no bulk un-trash statement found", statements.isNotEmpty())
        assertTrue(
            "an import lifts trashed chats out of the Trash, so it must drop " +
                "the archived flag as well: $statements",
            statements.all { unArchives.containsMatchIn(it) }
        )
    }
}
