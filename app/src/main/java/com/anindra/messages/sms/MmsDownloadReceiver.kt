package com.anindra.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs when a requested MMS transfer finishes: import it, then notify. */
class MmsDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DOWNLOAD_COMPLETE) return
        val pendingResult = goAsync()
        val wakeLock = ReceiverWakeLock.acquire(context, "mms-download")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
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
    }
}
