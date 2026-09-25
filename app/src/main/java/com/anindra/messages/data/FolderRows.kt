package com.anindra.messages.data

/** Column layout + SQL for the folder message rows: Spam & blocked → Messages
 *  and Trash → Messages.
 *
 *  Column [COL_NAME] is `c.name` — the contacts-resolved sender name (a saved
 *  contact's name, or the canonical address for unknown senders). The folder
 *  screens render that name and fall back to a formatted number only when it
 *  equals the address. Keeping the projection and the index contract here lets
 *  JUnit pin the mapping so a display number can't be smuggled into [COL_NAME]
 *  again. */
internal object FolderRows {
    const val COL_ID = 0
    const val COL_CONVERSATION_ID = 1
    const val COL_ADDRESS = 2
    const val COL_NAME = 3
    const val COL_BODY = 4
    const val COL_TIMESTAMP = 5
    const val COL_DELETED_AT = 6

    const val BLOCKED_SELECT =
        "SELECT m.id,m.conversation_id,c.address,c.name,m.body,m.timestamp " +
            "FROM messages m JOIN conversations c ON c.id=m.conversation_id " +
            "WHERE m.blocked_reason!='' AND m.deleted_at>0 ORDER BY m.timestamp DESC"

    const val TRASHED_SELECT =
        "SELECT m.id,m.conversation_id,c.address,c.name,m.body,m.timestamp,m.deleted_at " +
            "FROM messages m JOIN conversations c ON c.id=m.conversation_id " +
            "WHERE m.deleted_at>0 AND m.blocked_reason='' ORDER BY m.deleted_at DESC"

    fun blockedMessage(
        id: Long,
        conversationId: Long,
        address: String,
        name: String,
        body: String,
        timestamp: Long
    ): BlockedMessage = BlockedMessage(id, conversationId, address, name, body, timestamp)

    fun trashedMessage(
        id: Long,
        conversationId: Long,
        address: String,
        name: String,
        body: String,
        timestamp: Long,
        deletedAt: Long
    ): TrashedMessage = TrashedMessage(id, conversationId, address, name, body, timestamp, deletedAt)
}