package com.anindra.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anindra.messages.data.MmsSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the MMS notification-indication WAP push.
 *
 * The broadcast carries the raw `m-notification-ind` in its "data" extra and
 * nothing else: the platform does not file a message row for it when this app
 * is the default SMS app, so the notification has to be read and stored here
 * before there is anything for [MmsDownloader] to fetch. Every announced
 * message that was missed while the app was not the default handler is picked
 * up by the pending-row sweep that follows.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Logged before the filter: a broadcast the app receives but does not
        // match is the difference between "the carrier never sent it" and "the
        // manifest filter never let it in", and that difference is invisible
        // anywhere else.
        MmsTrace.i("MmsDownload", "WAP push broadcast: action=${intent.action} type=${intent.type}")
        if (!MmsSupport.isMmsWapPush(intent.action, intent.type)) return
        val data = intent.getByteArrayExtra("data")
        val subscriptionId = intent.getIntExtra("subscription", -1)
        val app = context.applicationContext
        val pendingResult = goAsync()
        val wakeLock = ReceiverWakeLock.acquire(context, "mms-notification")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                MmsDownloader.onWapPush(app, data, subscriptionId)
            } catch (t: Throwable) {
                // The default SMS app has to survive every incoming broadcast.
                MmsTrace.w("MmsDownload", "incoming MMS notification failed", t)
            } finally {
                wakeLock.safeRelease()
                pendingResult.finish()
            }
        }
    }
}
