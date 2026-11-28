package com.anindra.messages.data

/** How an inbound SMS is classified before it is stored. */
enum class InboundKind { NORMAL, BLOCKED_KEYWORD, BLOCKED_NUMBER }

/**
 * A normal message from a sender whose thread is in Trash starts a new thread.
 * The user deleted that history, so the next message must not revive it.
 *
 * Blocked traffic is the exception: its message is already soft-deleted, so it
 * belongs in the existing trash row rather than creating an active conversation
 * with a blank snippet.
 */
object InboundIngest {
    fun mayReuseTrashedConversation(kind: InboundKind): Boolean =
        kind == InboundKind.BLOCKED_KEYWORD || kind == InboundKind.BLOCKED_NUMBER
}
