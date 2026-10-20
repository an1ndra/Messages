package com.anindra.messages.sms

object BadgePolicy {
    /** The floor for a notification's number: a launcher that renders no number
     *  at all still needs a badge dot, and a conversation only ever notifies
     *  while it has unread content. */
    const val PER_NOTIFICATION = 1

    /** What one conversation's notification carries: its own unread count.
     *
     *  Launchers that render a badge aggregate the numbers of the app's live
     *  notifications, so the icon then sums to the unread *message* total, which
     *  is the number the inbox shows. Carrying a flat 1 made the badge count
     *  unread conversations instead, and a thread with five unpaid invoices
     *  counted once (#345). */
    fun badgeCount(unreadMessages: Int): Int = unreadMessages.coerceAtLeast(PER_NOTIFICATION)

    fun idsToDismiss(conversationId: Long?, addressHash: Int?): Pair<Int, Int>? {
        val id = conversationId?.toInt() ?: addressHash ?: return null
        return id to id
    }
}
