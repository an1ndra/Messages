package com.anindra.messages.sms

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Holds a short-lived partial WakeLock while a BroadcastReceiver does its
 * work. Without this, the system can suspend the CPU mid-receive in Doze and
 * the receiver's [android.content.BroadcastReceiver.PendingResult] can be
 * killed before `finish()` is called, losing incoming SMS / delivery
 * confirmations.
 *
 * Returns null on failure; callers should release what they get.
 */
internal object ReceiverWakeLock {
    private const val TAG = "ReceiverWakeLock"
    private const val TIMEOUT_MS = 10_000L

    fun acquire(context: Context, tagSuffix: String = "default"): PowerManager.WakeLock? {
        return try {
            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return null
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Messages:$tagSuffix")
            lock.setReferenceCounted(false)
            lock.acquire(TIMEOUT_MS)
            lock
        } catch (t: Throwable) {
            Log.w(TAG, "wakelock acquire failed: ${t.message}")
            null
        }
    }
}

/** No-op extension so receivers can ignore the null case in a single call. */
internal fun PowerManager.WakeLock?.safeRelease() {
    if (this == null) return
    try { if (isHeld) release() } catch (_: Throwable) {}
}
