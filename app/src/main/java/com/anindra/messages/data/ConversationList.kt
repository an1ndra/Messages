package com.anindra.messages.data

/**
 * Ordering + filtering rules for the conversation list, kept pure so the sort
 * is unit testable rather than inlined in the composable.
 */
object ConversationList {

    /**
     * Drops blocked conversations, keeps either the inbox or the archive, and
     * filters by [query] against name / address / snippet ([snippetFor] maps a
     * conversation's snippet to the text actually shown, so link hiding applies
     * to the search too).
     */
    fun filter(
        conversations: List<Conversation>,
        showArchived: Boolean,
        query: String,
        snippetFor: (Conversation) -> String
    ): List<Conversation> = conversations.filter { convo ->
        when {
            convo.blocked -> false
            showArchived -> convo.archived
            else -> !convo.archived
        }
    }.let { list ->
        if (query.isBlank()) list
        else list.filter {
            val snippet = snippetFor(it)
            it.name.contains(query, true) || it.address.contains(query) ||
                snippet.contains(query, true)
        }
    }

    /**
     * Unread-at-top: stable reorder — pinned stays on top, then unread
     * conversations above read ones, timestamp order preserved within a tier.
     * Off (the default) and archived views keep the repository's own order.
     */
    fun sort(
        conversations: List<Conversation>,
        unreadAtTop: Boolean,
        showArchived: Boolean
    ): List<Conversation> =
        if (unreadAtTop && !showArchived) {
            conversations.sortedWith(
                compareBy({ !it.pinned }, { if (it.unreadCount > 0) 0 else 1 }, { -it.timestamp })
            )
        } else {
            conversations
        }
}
