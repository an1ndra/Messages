package com.anindra.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs when a requested MMS transfer finishes: import it, then notify. */
class MmsDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DOWNLOAD_COMPLETE) return
        val uri = intent.getStringExtra(EXTRA_MMS_URI)
        // Only valid on the receiver thread; the platform reports the transfer
        // result as the PendingResult result code.
        val resultCode = resultCode
        val httpStatus = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0)
        // The announcement row carries the line, and the message it turns into
        // has to keep it.
        val subscriptionId = intent.getIntExtra(EXTRA_MMS_SUBSCRIPTION, -1)
        val pendingResult = goAsync()
        val wakeLock = ReceiverWakeLock.acquire(context, "mms-download")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                MmsDownloader.onComplete(
                    context.applicationContext, uri, resultCode, httpStatus, subscriptionId,
                )
                if (resultCode != android.app.Activity.RESULT_OK) return@launch
                val repo = (context.applicationContext as com.anindra.messages.MessagesApplication).repository
                for (mms in repo.importDownloadedMms()) {
                    NotificationHelper.show(context, mms.address, mms.body)
                }
            } catch (t: Throwable) {
                Log.w("MmsDownload", "import after download failed: ${t.message}")
            } finally {
                wakeLock.safeRelease()
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DOWNLOAD_COMPLETE = "com.anindra.messages.MMS_DOWNLOAD_COMPLETE"
        private const val EXTRA_MMS_URI = "mms_uri"
        internal const val EXTRA_MMS_SUBSCRIPTION = "mms_subscription_id"
    }
}
