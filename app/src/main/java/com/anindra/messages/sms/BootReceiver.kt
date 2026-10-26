package com.anindra.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A boot and an app update wipe the notification shade, and the launcher
 *  badge sums the numbers of the app's live notifications — so unread
 *  messages that survived in the database lost their badge with them (#345,
 *  "the phone turned off and on again; the counter no longer shows any
 *  number"). Re-post one silent notification per unread conversation. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        val wakeLock = ReceiverWakeLock.acquire(context, "badge-repost")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NotificationHelper.repostUnread(context)
            } catch (_: Exception) {
            } finally {
                wakeLock.safeRelease()
                pendingResult.finish()
            }
        }
    }
}
