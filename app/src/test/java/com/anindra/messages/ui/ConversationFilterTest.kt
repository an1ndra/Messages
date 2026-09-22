package com.anindra.messages.ui

import com.anindra.messages.data.Conversation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationFilterTest {

    private fun convo(archived: Boolean = false, blocked: Boolean = false) = Conversation(
        id = 1L, address = "+15551230000", name = "n", snippet = "", timestamp = 0L,
        unreadCount = 0, isMe = false, archived = archived, blocked = blocked
    )

    @Test
    fun inboxShowsOnlyUnblockedUnarchived() {
        assertTrue(matchesView(convo(), ConversationView.INBOX))
        assertFalse(matchesView(convo(archived = true), ConversationView.INBOX))
        assertFalse(matchesView(convo(blocked = true), ConversationView.INBOX))
    }

    @Test
    fun archivedHoldsUnblockedArchivedOnly() {
        assertTrue(matchesView(convo(archived = true), ConversationView.ARCHIVED))
        assertFalse(matchesView(convo(), ConversationView.ARCHIVED))
        assertFalse(matchesView(convo(archived = true, blocked = true), ConversationView.ARCHIVED))
    }

    @Test
    fun spamBlockedHoldsEveryBlockedConversation() {
        assertTrue(matchesView(convo(blocked = true), ConversationView.SPAM_BLOCKED))
        assertTrue(matchesView(convo(archived = true, blocked = true), ConversationView.SPAM_BLOCKED))
        assertFalse(matchesView(convo(), ConversationView.SPAM_BLOCKED))
        assertFalse(matchesView(convo(archived = true), ConversationView.SPAM_BLOCKED))
    }
}
