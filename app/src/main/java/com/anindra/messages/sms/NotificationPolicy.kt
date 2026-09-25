package com.anindra.messages.sms

/** Decides whether an incoming-message notification is suppressed because the
 *  user is currently reading that exact thread. */
object NotificationPolicy {
    fun skipForOpenThread(appInForeground: Boolean, threadOpen: Boolean): Boolean =
        appInForeground && threadOpen
}
