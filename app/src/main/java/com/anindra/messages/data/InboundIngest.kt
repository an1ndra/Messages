package com.anindra.messages.data

/** How an inbound SMS is classified before it is stored. */
enum class InboundKind { NORMAL, BLOCKED_KEYWORD, BLOCKED_NUMBER }

/**
 * Storing a message that is itself blocked must not pull a trashed thread back
 * into the inbox. The message is already soft-deleted, so restoring the
 * conversation only resurrects a row the user emptied, and since the snippet
 * only ever picks up non-deleted messages it comes back looking blank.
 */
object InboundIngest {
    fun restoresTrashedConversation(kind: InboundKind): Boolean = kind == InboundKind.NORMAL
}
