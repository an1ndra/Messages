package com.anindra.messages.sms

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicReference

/**
 * Lightweight foreground + open-conversation tracker that the SMS layer reads
 * before posting a notification or playing a sound. The chat screen updates
 * these on enter/leave so that an incoming SMS while the user is reading that
 * exact thread doesn't fire a redundant system notification + MediaPlayer.
 */
internal object ForegroundTracker {
    private val openAddress = AtomicReference<String?>(null)
    private const val PREFS_NAME = "foreground_tracker"
    private const val KEY_OPEN_ADDRESS = "open_address"

    @Volatile private var foreground: Boolean = false

    val isAppInForeground: Boolean get() = foreground
    fun isConversationOpen(address: String?): Boolean {
        val addr = address?.replace(Regex("\\D"), "") ?: return false
        if (openAddress.get() == addr) return true
        return false
    }

    fun getOpenAddress(): String? = openAddress.get()

    fun setAppForeground(value: Boolean) { foreground = value }
    fun setOpenConversation(address: String?) {
        val normalized = address?.replace(Regex("\\D"), "")
        openAddress.set(normalized)
    }

    /** Check if conversation is open using shared preferences (survives process death). */
    fun isConversationOpenFromPrefs(context: android.content.Context, address: String?): Boolean {
        val addr = address?.replace(Regex("\\D"), "") ?: return false
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getString(KEY_OPEN_ADDRESS, null) == addr
    }
}
