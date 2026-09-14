package com.anindra.messages.sms

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicReference

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

    fun isConversationOpen(address: String?): Boolean =
        address != null && address == openAddress.get()

    fun setAppForeground(value: Boolean) { foreground = value }

    fun setOpenConversation(address: String?) {
        openAddress.set(address)
    }

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
        val stored = prefs.getString(KEY_OPEN_ADDRESS, null)
        return address != null && stored != null && address == stored
    }

    /** Called from SmsReceiver to persist current state after checking. */
    fun persistIfForeground(context: Context) {
        if (foreground) persist(context)
    }
}
