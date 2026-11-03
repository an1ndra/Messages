package com.anindra.messages.sms

import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight foreground + open-conversation tracker that the SMS layer reads
 * before posting a notification or playing a sound. The chat screen updates
 * these on enter/leave so that an incoming SMS while the user is reading that
 * exact thread doesn't fire a redundant system notification.
 *
 * More than one chat can be on screen at once since a bubble hosts its own
 * activity next to the full-screen one, so open conversations are counted
 * rather than held in a single slot: closing the bubble must not tell the SMS
 * layer that the chat still visible in the app window is gone.
 *
 * The open addresses are in-memory only. They used to be restored from
 * SharedPreferences on startup, which let a stale address from an older build
 * suppress notifications for that sender forever; the value now only ever
 * comes from a chat that is genuinely on screen.
 */
internal object ForegroundTracker {
    private val openAddresses = ConcurrentHashMap<String, Int>()
    @Volatile private var foreground: Boolean = false

    val isAppInForeground: Boolean get() = foreground

    fun isConversationOpen(address: String?): Boolean =
        address != null && openAddresses.containsKey(address)

    fun setAppForeground(value: Boolean) { foreground = value }

    fun openConversation(address: String?) {
        if (address == null) return
        openAddresses.merge(address, 1, Int::plus)
    }

    fun closeConversation(address: String?) {
        if (address == null) return
        openAddresses.compute(address) { _, count ->
            when {
                count == null -> null
                count <= 1 -> null
                else -> count - 1
            }
        }
    }

    /** Test seam: forget every window's registration. */
    internal fun reset() {
        openAddresses.clear()
        foreground = false
    }
}
