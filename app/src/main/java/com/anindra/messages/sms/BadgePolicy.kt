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

    /** Notification ids to cancel when a sender moves to Spam & blocked. The
     *  launcher badge sums the numbers of live notifications, and cancelling
     *  is the only way a blocked sender's count can drop: the messages that
     *  raised it stay unread in the blocked folder, and the messages that
     *  follow a block are never posted (#345). Entry per conversation id,
     *  plus the address-hash id [idsToDismiss] falls back to when the sender
     *  had no conversation row, duplicates removed. */
    fun idsToDismissOnBlock(conversationIds: List<Long>, address: String?): List<Int> {
        val ids = LinkedHashSet<Int>()
        conversationIds.forEach { idsToDismiss(it, null)?.let { p -> ids += p.first } }
        if (address != null) idsToDismiss(null, address.hashCode())?.let { ids += it.first }
        return ids.toList()
    }
}
