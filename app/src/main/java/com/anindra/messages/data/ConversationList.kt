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
