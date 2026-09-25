package com.anindra.messages.sms

/**
 * The launcher icon badge. Kept pure so it is unit tested.
 */
object BadgePolicy {
    /**
     * The number carried by one conversation's notification.
     *
     * It has to be **1**, not the unread total. Launchers that render a count
     * *aggregate across the app's active notifications* - Lawnchair sums them,
     * and the badge was observed rendering 45 for three notifications that each
     * carried a total of 15. Publishing 1 per conversation makes that sum the
     * number of unread conversations, which is both correct and what messaging
     * apps conventionally show.
     */
    const val PER_NOTIFICATION = 1

    /** Never publish a negative or non-positive count: launchers treat 0 as "no badge". */
    fun badgeCount(conversations: Int): Int = conversations.coerceAtLeast(PER_NOTIFICATION)

    /**
     * Ids to dismiss when a conversation is opened, as (untagged, "failed"-
     * tagged) pairs for that one conversation only. Dismissing everything (the
     * old cancelAll behaviour) wiped other unread conversations' notifications,
     * and the badge is derived from active notifications, so the count
     * disappeared as soon as any chat was opened.
     */
    fun idsToDismiss(conversationId: Long?, addressHash: Int?): Pair<Int, Int>? {
        val id = conversationId?.toInt() ?: addressHash ?: return null
        return id to id
    }
}
