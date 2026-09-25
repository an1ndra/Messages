package com.anindra.messages.sms

object BadgePolicy {
    /** Launchers aggregate this across the app's active notifications, so it has
     *  to be 1 - a per-conversation total renders as nonsense once two are live. */
    const val PER_NOTIFICATION = 1

    fun badgeCount(conversations: Int): Int = conversations.coerceAtLeast(PER_NOTIFICATION)

    fun idsToDismiss(conversationId: Long?, addressHash: Int?): Pair<Int, Int>? {
        val id = conversationId?.toInt() ?: addressHash ?: return null
        return id to id
    }
}
