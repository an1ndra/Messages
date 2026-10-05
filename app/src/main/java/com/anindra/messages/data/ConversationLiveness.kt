package com.anindra.messages.data

/**
 * What keeps a conversation out of the trash when it is left with nothing typed.
 *
 * Extracted from the SQL so the rule is testable without a database: the query
 * only has to count rows, and every branch here is plain logic.
 */
object ConversationLiveness {

    /**
     * True when the conversation still has something worth showing, so leaving
     * it must not trash it.
     *
     * @param messageCount rows in `messages` for this conversation.
     * @param pendingScheduled rows in `scheduled_messages` — a queued message
     *   the user is waiting on, which lives outside `messages` entirely.
     * @param draft the draft text; a non-blank value keeps the chat visible.
     */
    fun keepAlive(messageCount: Int, pendingScheduled: Int, draft: String): Boolean =
        messageCount > 0 || pendingScheduled > 0 || draft.isNotBlank()
}