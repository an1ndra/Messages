package com.anindra.messages.data

import com.anindra.messages.ui.SelectionToolbar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** #394: bookmarked messages. The flag is local (the provider has no column),
 *  so the wiring — selection toolbar, bubble badge, Starred search scope and the
 *  schema migration — is asserted here, and the behaviour on a device is covered
 *  by scripts/test-starred-messages.sh. */
class StarredMessagesTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun src(path: String) = File(main, path).readText()

    @Test
    fun theStarActionIsOfferedForAnySelection() {
        assertTrue("nothing selected: no star action", !SelectionToolbar.showStar(0))
        assertTrue("one message: star action", SelectionToolbar.showStar(1))
        assertTrue("several messages: star action", SelectionToolbar.showStar(3))
    }

    @Test
    fun aMessageIsUnstarredByDefault() {
        val message = Message(
            id = 1,
            conversationId = 1,
            body = "hello",
            timestamp = 0,
            isMe = false,
            status = "received"
        )
        assertEquals(false, message.starred)
    }

    @Test
    fun theSchemaMigratesAndKeepsTheFlagLocal() {
        val repo = src("java/com/anindra/messages/data/Repository.kt")
        assertTrue(
            "the upgrade block must add the column for existing installs",
            repo.contains("ALTER TABLE messages ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
        )
        assertTrue(
            "the column must exist in a fresh database too",
            Regex("""starred INTEGER NOT NULL DEFAULT 0\)""").containsMatchIn(repo)
        )
        assertTrue(
            "the browse query reads only starred, visible rows",
            repo.contains("WHERE m.starred=1 AND m.deleted_at=0")
        )
        assertTrue(
            "toggling must write the column, not a copy",
            repo.contains("UPDATE messages SET starred=?")
        )
    }

    @Test
    fun theChatOffersStarFromTheSelectionToolbarAndBadgesStarredBubbles() {
        val chat = src("java/com/anindra/messages/ui/ChatScreen.kt")
        assertTrue("star action wired to the view model", chat.contains("vm.setStarred("))
        assertTrue("the toolbar shows a star icon", chat.contains("R.string.chat_star"))
        assertTrue("starred bubbles carry a badge", chat.contains("R.string.chat_message_starred"))
    }

    @Test
    fun theHomeSearchHasAStarredScopeThatListsMessages() {
        val list = src("java/com/anindra/messages/ui/ConversationsScreen.kt")
        assertTrue("scope chips", list.contains("SearchScopeChips("))
        assertTrue("message rows in the Starred scope", list.contains("StarredHitRow("))
        assertTrue("the scope queries bookmarked messages", list.contains("vm.starredMessages("))
        assertTrue("tap opens the chat that owns the message", list.contains("hit.conversationId"))
    }

    @Test
    fun everyLocaleCarriesTheNewStrings() {
        val res = main.resolve("res")
        val base = File(res, "values/strings_chat.xml").readText()
        val baseConversations = File(res, "values/strings_conversations.xml").readText()
        val keys = Regex("""name="(chat_(star|unstar|starred|unstarred|message_starred))"""")
            .findAll(base).map { it.groupValues[1] }.toSet()
        val convKeys = Regex("""name="(search_scope_\w+|starred_empty(_hint)?)"""")
            .findAll(baseConversations).map { it.groupValues[1] }.toSet()
        assertEquals("expected five chat keys and nine search keys", 14, keys.size + convKeys.size)

        res.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .forEach { dir ->
                val chat = File(dir, "strings_chat.xml")
                val conv = File(dir, "strings_conversations.xml")
                if (!chat.exists() || !conv.exists()) return@forEach
                val chatText = chat.readText()
                val convText = conv.readText()
                keys.forEach { k ->
                    assertTrue("${dir.name}: missing $k", chatText.contains("name=\"$k\""))
                }
                convKeys.forEach { k ->
                    assertTrue("${dir.name}: missing $k", convText.contains("name=\"$k\""))
                }
            }
    }
}
