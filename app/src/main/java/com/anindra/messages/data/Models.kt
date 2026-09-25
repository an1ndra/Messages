package com.anindra.messages.data

data class Conversation(
    val id: Long,
    val address: String,
    val name: String,
    val snippet: String,
    val timestamp: Long,
    val unreadCount: Int,
    val isMe: Boolean,
    val archived: Boolean = false,
    val blocked: Boolean = false,
    val pinned: Boolean = false,
    val draft: String = "",
    val draftDate: Long = 0,
    val deletedAt: Long = 0,
    val display: String = address,
    val deletedReason: String = TrashReason.MANUAL
)

data class Message(
    val id: Long,
    val conversationId: Long,
    val body: String,
    val timestamp: Long,
    val isMe: Boolean,
    val status: String,
    val mediaType: String = "text",
    val mediaUri: String = "",
    val reactions: Map<String, Int> = emptyMap(),
    val locked: Boolean = false,
    val subId: Int = -1,
    val transport: String = "sms",
    val deliveredAt: Long = 0
)

data class BlockedNumber(
    val id: Long = 0,
    val number: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** A keyword-blocked SMS parked in the "Spam & blocked" folder. */
data class BlockedMessage(
    val id: Long,
    val conversationId: Long,
    val address: String,
    val name: String,
    val body: String,
    val timestamp: Long,
    val blockedReason: String
)

/** A manually deleted message kept in Trash (message-level soft delete). */
data class TrashedMessage(
    val id: Long,
    val conversationId: Long,
    val address: String,
    val name: String,
    val body: String,
    val timestamp: Long,
    val deletedAt: Long
)

data class ScheduledMessage(
    val id: Long = 0,
    val address: String,
    val body: String,
    val timestamp: Long,
    val conversationId: Long,
    val subId: Int = -1
)
