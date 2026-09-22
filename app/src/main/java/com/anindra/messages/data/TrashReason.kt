package com.anindra.messages.data

/** Why a conversation was moved to Trash. Persisted in conversations.deleted_reason. */
object TrashReason {
    const val MANUAL = "manual"
    const val BLOCKED_KEYWORD = "blocked_keyword"

    fun isBlockedKeyword(reason: String): Boolean = reason == BLOCKED_KEYWORD
}
