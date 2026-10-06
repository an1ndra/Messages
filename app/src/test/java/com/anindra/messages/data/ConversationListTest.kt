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
        // Matching QUIK, the inbox is newest-first by default. Unread-at-top is
        // still available, but opted into: with it on, an older unread thread can
        // sit above a newer read one, which hides the actual latest message.
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
    fun aFormattedPhoneNumberFindsTheConversation() {
        // The UI lets you type the number as it is dialled; the address is E.164.
        val list = listOf(
            convo(1, name = "Zokii", address = "+15550001234"),
            convo(2, name = "Other", address = "+15550009999")
        )
        assertEquals(listOf(1L), filter(list, query = "555-000-1234"))
        assertEquals(listOf(1L), filter(list, query = "5550001234"))
        assertEquals(listOf(1L), filter(list, query = "+1 (555) 000-1234"))
        assertEquals(listOf(1L), filter(list, query = "0001234"))
        assertEquals(emptyList<Long>(), filter(list, query = "5550000000"))
    }

    @Test
    fun aContactSavedInNationalFormatIsFoundByItsNumber() {
        // Issue #284: a thread stores its address in E.164, but the number a
        // saved contact is dialled and typed as keeps the local trunk zero and
        // drops the country code, so neither digit run is a suffix of the
        // other. Searching that contact by number has to find the thread.
        val list = listOf(
            convo(1, name = "Zokii", address = "+919876543210"),
            convo(2, name = "Other", address = "+919876111111")
        )
        assertEquals(listOf(1L), filter(list, query = "09876543210"))
        assertEquals(listOf(1L), filter(list, query = "09876 543 210"))
        assertEquals(listOf(1L), filter(list, query = "00919876543210"))
        assertEquals(emptyList<Long>(), filter(list, query = "09876500000"))
        // The number rule is additive on top of the plain substring tests, so a
        // short run still finds a thread whose stored address literally spells
        // it out. `matchesNumber` itself is covered in AddressIdentityTest.
        assertEquals(listOf(1L), filter(list, query = "6543210"))
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

    @Test
    fun aHitAnywhereInTheThreadSurfacesTheConversation() {
        // The snippet is only the newest message. A hit in an older message
        // arrives as a conversation id from the repository, and that alone must
        // surface the thread even though no visible text contains the query.
        val list = listOf(convo(1, snippet = "newest words"), convo(2, snippet = "newest words"))
        assertEquals(
            listOf(2L),
            ids(ConversationList.filter(list, false, "buried", setOf(2L)) { it.snippet })
        )
        assertEquals(
            emptyList<Long>(),
            ids(ConversationList.filter(list, false, "buried", emptySet()) { it.snippet })
        )
    }
}
