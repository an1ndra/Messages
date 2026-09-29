package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationListTest {

    private fun convo(
        id: Long,
        unread: Int = 0,
        pinned: Boolean = false,
        archived: Boolean = false,
        blocked: Boolean = false,
        name: String = "Name $id",
        address: String = "+1555000$id",
        snippet: String = "Snippet $id",
        timestamp: Long = id
    ) = Conversation(
        id = id, address = address, name = name, snippet = snippet,
        timestamp = timestamp, unreadCount = unread, isMe = false,
        archived = archived, blocked = blocked, pinned = pinned
    )

    private fun ids(list: List<Conversation>) = list.map { it.id }

    private fun filter(
        list: List<Conversation>,
        query: String = "",
        showArchived: Boolean = false
    ) = ids(ConversationList.filter(list, showArchived, query) { it.snippet })

    private fun sort(
        list: List<Conversation>,
        unreadAtTop: Boolean,
        showArchived: Boolean = false
    ) = ids(ConversationList.sort(list, unreadAtTop, showArchived))

    // --- default ---------------------------------------------------------

    @Test
    fun unreadAtTopDefaultsToOff() {
        assertEquals("unread_at_top_enabled", SettingsStore.KEY_UNREAD_AT_TOP_ENABLED)
        assertEquals(false, SettingsStore.DEFAULTS_UNREAD_AT_TOP)
    }

    @Test
    fun sortIsANoOpWithUnreadAtTopOff() {
        val list = listOf(convo(1, timestamp = 10), convo(2, unread = 3, timestamp = 20))
        assertEquals(listOf(1L, 2L), sort(list, unreadAtTop = false))
    }

    // --- sort ------------------------------------------------------------

    @Test
    fun unreadSortPutsUnreadAboveRead() {
        val list = listOf(convo(1, unread = 0, timestamp = 30), convo(2, unread = 2, timestamp = 10))
        assertEquals(listOf(2L, 1L), sort(list, unreadAtTop = true))
    }

    @Test
    fun pinnedStaysAboveUnread() {
        val list = listOf(
            convo(1, unread = 5, timestamp = 30),
            convo(2, pinned = true, timestamp = 10)
        )
        assertEquals(listOf(2L, 1L), sort(list, unreadAtTop = true))
    }

    @Test
    fun timestampOrderIsNewestFirstWithinATier() {
        val list = listOf(convo(1, unread = 1, timestamp = 10), convo(2, unread = 1, timestamp = 30))
        assertEquals(listOf(2L, 1L), sort(list, unreadAtTop = true))
    }

    @Test
    fun archivedViewIsNeverReordered() {
        val list = listOf(convo(1, unread = 0), convo(2, unread = 9))
        assertEquals(listOf(1L, 2L), sort(list, unreadAtTop = true, showArchived = true))
    }

    // --- filter ----------------------------------------------------------

    @Test
    fun blockedConversationsAreNeverShown() {
        assertEquals(listOf(1L), filter(listOf(convo(1), convo(2, blocked = true))))
    }

    @Test
    fun inboxAndArchiveAreSeparateViews() {
        val list = listOf(convo(1), convo(2, archived = true))
        assertEquals(listOf(1L), filter(list, showArchived = false))
        assertEquals(listOf(2L), filter(list, showArchived = true))
    }

    @Test
    fun queryMatchesNameAddressOrSnippet() {
        val list = listOf(convo(1, name = "Alice"), convo(2, name = "Bob", address = "+15559999"))
        assertEquals(listOf(1L), filter(list, query = "ali"))
        assertEquals(listOf(2L), filter(list, query = "9999"))
        assertEquals(listOf(1L, 2L), filter(list, query = "Snippet"))
        assertEquals(emptyList<Long>(), filter(list, query = "zzz"))
    }

    @Test
    fun searchMatchesTheShownSnippetNotTheStoredOne() {
        // "Hide links" swaps the snippet for its redacted form, and the search
        // has to use the swapped one so a hidden link is not still findable.
        val list = listOf(convo(1, snippet = "see https://example.com now"))
        val stored = ids(ConversationList.filter(list, false, "example.com") { it.snippet })
        val shown = ids(ConversationList.filter(list, false, "example.com") { "see  now" })
        assertEquals(listOf(1L), stored)
        assertEquals(emptyList<Long>(), shown)
    }
}
