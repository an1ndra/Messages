package com.anindra.messages.sms

import java.util.concurrent.atomic.AtomicReference

/**
 * Lightweight foreground + open-conversation tracker that the SMS layer reads
 * before posting a notification or playing a sound. The chat screen updates
 * these on enter/leave so that an incoming SMS while the user is reading that
 * exact thread doesn't fire a redundant system notification.
 *
 * The open address is in-memory only. It used to be restored from
 * SharedPreferences on startup, which let a stale address from an older build
 * suppress notifications for that sender forever; the value now only ever
 * comes from a chat that is genuinely on screen.
 */
internal object ForegroundTracker {
    private val openAddress = AtomicReference<String?>(null)
    @Volatile private var foreground: Boolean = false

    val isAppInForeground: Boolean get() = foreground

    fun isConversationOpen(address: String?): Boolean =
        address != null && address == openAddress.get()

    fun setAppForeground(value: Boolean) { foreground = value }

    fun setOpenConversation(address: String?) {
        openAddress.set(address)
    }
}
