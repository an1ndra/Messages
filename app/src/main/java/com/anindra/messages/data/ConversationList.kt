package com.anindra.messages.data

/**
 * Ordering + filtering rules for the conversation list, kept pure so the sort
 * is unit testable rather than inlined in the composable.
 */
object ConversationList {

    /**
     * Drops blocked conversations, keeps either the inbox or the archive, then
     * filters by [query].
     *
     * A number query ([AddressIdentity.isNumberQuery]) filters on address only.
     * A text query filters against name / address / snippet ([snippetFor] maps a
     * conversation's snippet to the text actually shown, so link hiding applies
     * to the search too), plus [messageMatchIds] — conversations with a hit
     * anywhere in their history (see `Repository.conversationIdsMatchingMessage`),
     * so a word buried in an old message still surfaces its thread.
     */
    fun filter(
        conversations: List<Conversation>,
        showArchived: Boolean,
        query: String,
        messageMatchIds: Set<Long> = emptySet(),
        snippetFor: (Conversation) -> String
    ): List<Conversation> = conversations.filter { convo ->
        when {
            convo.blocked -> false
            showArchived -> convo.archived
            else -> !convo.archived
        }
    }.let { list ->
        if (query.isBlank()) list
        else if (AddressIdentity.tooShortToBeNumber(query)) {
            // Still being typed. Filtering to nothing here reads as a bug, so the
            // list holds until the query is a real number. Checked before the
            // number-query branch, which would otherwise match nothing.
            list
        } else if (AddressIdentity.isNumberQuery(query)) {
            // A number is a contact and nothing else. Not name, not snippet, not
            // messageMatchIds: those are what listed every chat that merely
            // mentioned the number, and what let a digit inside a sender name or
            // a snippet match (issue #284).
            list.filter { AddressIdentity.matchesNumber(it.address, query) }
        } else {
            list.filter {
                val snippet = snippetFor(it)
                it.id in messageMatchIds ||
                    it.name.contains(query, true) || it.address.contains(query, true) ||
                    snippet.contains(query, true) ||
                    AddressIdentity.matchesNumber(it.address, query)
            }
        }
    }

    /**
     * For a word search: conversations whose name (or address) matches go first,
     * those found only through a message go after them. Each group keeps the
     * order it came in, so recency — or unread-at-top — still decides within it.
     *
     * Without this a contact only tops the results by being the most recent
     * thread, and chats that merely mention the name sit above it. A number
     * query already returns contacts only, and a number still being typed leaves
     * the list alone, so both are returned unchanged.
     */
    fun nameMatchesFirst(conversations: List<Conversation>, query: String): List<Conversation> {
        if (query.isBlank() ||
            AddressIdentity.tooShortToBeNumber(query) ||
            AddressIdentity.isNumberQuery(query)
        ) return conversations
        val (named, mentioned) = conversations.partition {
            it.name.contains(query, true) || it.address.contains(query, true)
        }
        return named + mentioned
    }

    /**
     * The query a chat opened from search should highlight, or "" for none.
     *
     * A conversation that was found because its name or address matches is
     * opened plainly: jumping to the first message that happens to contain the
     * name would be a surprise, since the contact was what the user looked for.
     * The scroll to a message is kept for conversations found only through a
     * message.
     */
    fun handoffQuery(conversation: Conversation, query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return ""
        return if (conversation.name.contains(q, true) || conversation.address.contains(q, true)) "" else q
    }

    /**
     * Unread-at-top: stable reorder — pinned stays on top, then unread
     * conversations above read ones, timestamp order preserved within a tier.
     * On by default, so anything unread is visible without scrolling; archived
     * views keep the repository's own order, since "unread" is not what you are
     * looking for there.
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
