package com.anindra.messages.sms

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.anindra.messages.data.MmsProviderReader
import com.anindra.messages.data.MmsSupport
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches incoming MMS from the carrier. Since KitKat the platform no longer
 * downloads MMS on the app's behalf: the default SMS app gets a
 * `WAP_PUSH_DELIVER` broadcast for an announced-but-empty message row and has to
 * call [android.telephony.SmsManager.downloadMultimediaMessage] itself. Until
 * that fetch happens the provider row stays `m_type=130` with no parts, which is
 * why an incoming MMS used to be dropped entirely.
 */
internal object MmsDownloader {
    private const val TAG = "MmsDownload"
    private val attempts = ConcurrentHashMap<String, Long>()

    fun onWapPush(context: Context, uri: Uri) {
        request(context, uri)
    }

    /** Re-requests any announced MMS still waiting to be fetched, covering a
     *  WAP broadcast that was missed while the app was not the default handler. */
    fun requestPending(context: Context) {
        val ids = try {
            MmsProviderReader(context.contentResolver).pendingDownloadIds()
        } catch (t: Throwable) {
            Log.w(TAG, "pending MMS query failed: ${t.message}")
            emptyList()
        }
        for (id in ids) request(context, Uri.parse(MmsSupport.messageContentUri(id)))
    }

    fun request(context: Context, uri: Uri): Boolean {
        val key = uri.toString()
        val now = System.currentTimeMillis()
        if (!MmsSupport.shouldRetryDownload(attempts[key] ?: 0L, now)) return false
        attempts[key] = now
        return try {
            val completion = PendingIntent.getBroadcast(
                context, key.hashCode(),
                Intent(MmsDownloadReceiver.ACTION_DOWNLOAD_COMPLETE)
                    .setPackage(context.packageName)
                    .setComponent(ComponentName(context, MmsDownloadReceiver::class.java)),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            SmsSender.manager(context, -1).downloadMultimediaMessage(
                context, context.packageName, uri, null, completion
            )
            Log.i(TAG, "MMS download requested for $key")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "MMS download request failed for $key: ${t.message}")
            false
        }
    }
}
