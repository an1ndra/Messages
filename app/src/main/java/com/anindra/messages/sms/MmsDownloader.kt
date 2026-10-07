package com.anindra.messages.sms

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.anindra.messages.data.MmsProviderReader
import com.anindra.messages.data.MmsRetry
import com.anindra.messages.data.MmsSupport
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches incoming MMS from the carrier. Since KitKat the platform no longer
 * downloads MMS on the app's behalf: the default SMS app gets a
 * `WAP_PUSH_DELIVER` broadcast for an announced-but-empty message row and has to
 * call [android.telephony.SmsManager.downloadMultimediaMessage] itself. Until
 * that fetch happens the provider row stays `m_type=130` with no parts, which
 * is why an incoming MMS used to be dropped entirely.
 *
 * The platform owns the HTTP transaction, including the M-NotifyResp.ind
 * acknowledgement and the RetrieveConf write, so this app deliberately does not
 * send a second notification response. What the app does own is retry policy: a
 * missing data network and a message the carrier has discarded need very
 * different treatment, which a single fixed cooldown cannot express.
 */
internal object MmsDownloader {
    private const val TAG = "MmsDownload"
    private const val EXTRA_MMS_URI = "mms_uri"

    private class Attempt(
        var count: Int = 0,
        var lastAt: Long = 0L,
        var location: String? = null
    )

    private val attempts = ConcurrentHashMap<String, Attempt>()

    fun onWapPush(context: Context) {
        requestPending(context)
    }

    /** Re-requests any announced MMS still waiting to be fetched, covering a
     *  WAP broadcast that was missed while the app was not the default handler. */
    fun requestPending(context: Context) {
        val rows = try {
            MmsProviderReader(context.contentResolver).pendingDownloads()
        } catch (t: Throwable) {
            Log.w(TAG, "pending MMS query failed: ${t.message}")
            emptyList()
        }
        for (row in rows) request(context, row)
    }

    /**
     * Asks the platform to fetch the announced MMS [row] from its
     * Content-Location. Refused while a previous attempt for the same message
     * is still inside its backoff window, and impossible at all without a
     * location.
     */
    fun request(context: Context, row: MmsSupport.PendingDownload): Boolean {
        val location = MmsSupport.downloadLocation(row.contentLocation)
        if (location == null) {
            Log.w(TAG, "announced MMS ${row.id} has no Content-Location to fetch")
            return false
        }
        val key = MmsSupport.messageContentUri(row.id)
        val uri = Uri.parse(key)
        val now = System.currentTimeMillis()
        val attempt = attempts.computeIfAbsent(key) { Attempt() }
        if (!MmsRetry.shouldRetryNow(
                MmsRetry.AUTO_RETRY, attempt.count, attempt.lastAt, now
            )
        ) {
            Log.i(TAG, "MMS download for $key still backing off (${attempt.count} attempts)")
            return false
        }
        attempt.count++
        attempt.lastAt = now
        attempt.location = location
        return try {
            val completion = PendingIntent.getBroadcast(
                context, key.hashCode(),
                Intent(MmsDownloadReceiver.ACTION_DOWNLOAD_COMPLETE)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_MMS_URI, key)
                    .setComponent(ComponentName(context, MmsDownloadReceiver::class.java)),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            SmsSender.manager(context, -1).downloadMultimediaMessage(
                context, location, uri, null, completion
            )
            Log.i(
                TAG,
                "MMS download requested for $key from $location (attempt ${attempt.count})"
            )
            true
        } catch (t: Throwable) {
            Log.w(TAG, "MMS download request failed for $key: ${t.message}")
            attempt.count--
            false
        }
    }

    /**
     * Applies the result the platform reported for a finished download. A
     * transient failure is retried while the attempt budget lasts; a message the
     * carrier has discarded is forgotten so it stops being reconsidered.
     */
    fun onComplete(context: Context, uri: String?, resultCode: Int) {
        val key = uri ?: return
        val outcome = MmsRetry.classify(resultCode)
        if (outcome == MmsRetry.SUCCEEDED) {
            attempts.remove(key)
            return
        }
        if (outcome == MmsRetry.NO_RETRY) {
            Log.w(TAG, "MMS $key permanently failed (code $resultCode), not retrying")
            attempts.remove(key)
            return
        }
        val attempt = attempts[key] ?: return
        if (outcome == MmsRetry.AUTO_RETRY) {
            val delay = MmsRetry.delayFor(attempt.count)
            Log.i(TAG, "MMS $key failed (code $resultCode), retrying in ${delay}ms")
            val id = key.substringAfterLast('/').toLongOrNull() ?: return
            scheduleRetry(
                context, MmsSupport.PendingDownload(id, attempt.location), delay
            )
        } else {
            // Manual retry: the user has to decide, so keep the row eligible but
            // stop the automatic loop.
            Log.w(TAG, "MMS $key needs manual retry (code $resultCode)")
            attempts.remove(key)
        }
    }

    private fun scheduleRetry(
        context: Context,
        row: MmsSupport.PendingDownload,
        delayMs: Long
    ) {
        val app = context.applicationContext
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching { request(app, row) }
                .onFailure { Log.w(TAG, "scheduled retry failed: ${it.message}") }
        }, delayMs)
    }
}
