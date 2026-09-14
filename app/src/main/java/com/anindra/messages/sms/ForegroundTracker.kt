package com.anindra.messages.sms

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/** Extracts only digits from an address (removes +, spaces, parens, dashes). */
private fun digitsOnly(s: String): String = s.filter { it.isDigit() }

/**
 * Lightweight foreground + open-conversation tracker that the SMS layer reads
 * before posting a notification or playing a sound. The chat screen updates
 * these on enter/leave so that an incoming SMS while the user is reading that
 * exact thread doesn't fire a redundant system notification + MediaPlayer.
 *
 * Persistence: uses SharedPreferences so the open address survives process death
 * (the SMS receiver runs in a separate process that gets killed after SMS delivery).
 */
internal object ForegroundTracker {
    private const val PREFS_NAME = "foreground_tracker"
    private const val KEY_OPEN_ADDRESS = "open_address"

    private val openAddress = AtomicReference<String?>(null)
    @Volatile private var foreground: Boolean = false

    val isAppInForeground: Boolean get() = foreground

    fun isConversationOpen(address: String?): Boolean {
        val addr = address?.let { digitsOnly(it) } ?: return false
        val stored = openAddress.get()?.let { digitsOnly(it) }
        return addr == stored
    }

    fun setAppForeground(value: Boolean) {
        foreground = value
    }

    fun setOpenConversation(address: String?, context: Context? = null) {
        openAddress.set(address)
        if (context != null) persist(context)
    }

    fun getOpenAddress(): String? = openAddress.get()

    /** Persist state to SharedPreferences (survives process death). */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        openAddress.set(prefs.getString(KEY_OPEN_ADDRESS, null))
    }

    private fun persist(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_OPEN_ADDRESS, openAddress.get())
            .apply()
    }

    /** Check if a conversation is open using persisted state (for broadcast receiver). */
    fun isConversationOpenFromPrefs(context: Context, address: String?): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val addr = address?.let { digitsOnly(it) } ?: return false
        val stored = prefs.getString(KEY_OPEN_ADDRESS, null)?.let { digitsOnly(it) }
        return addr == stored
    }
}
