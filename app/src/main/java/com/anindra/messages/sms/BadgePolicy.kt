package com.anindra.messages.sms

/**
 * The launcher icon badge is the total unread count. Two things drive it: the
 * number carried on the notification, and whether other conversations'
 * notifications survive when one chat is opened. Kept pure so it is unit
 * tested.
 */
object BadgePolicy {
    /**
     * The count to publish on a notification. Android launchers render this as
     * the app-icon badge, and a number of them show nothing at all when it is
     * absent. Never publish a negative count.
     */
    fun badgeCount(totalUnread: Int): Int = totalUnread.coerceAtLeast(0)

    /** A notification should carry a number only when something is unread. */
    fun publishesNumber(totalUnread: Int): Boolean = totalUnread > 0

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
