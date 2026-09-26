package com.anindra.messages.sms

/** Decides whether an incoming-message notification is suppressed because the
 *  user is currently reading that exact thread. */
object NotificationPolicy {
    fun skipForOpenThread(appInForeground: Boolean, threadOpen: Boolean): Boolean =
        appInForeground && threadOpen

    /** Whether an arriving message must bump its conversation's unread count. A
     *  message that lands in the thread the user is reading is already on screen,
     *  so counting it pushed the badge up for a chat that had no unread content. */
    fun countsAsUnread(appInForeground: Boolean, threadOpen: Boolean): Boolean =
        !skipForOpenThread(appInForeground, threadOpen)
}
