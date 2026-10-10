package com.anindra.messages.sms

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.anindra.messages.data.MmsProviderReader
import com.anindra.messages.data.MmsRetry
import com.anindra.messages.data.MmsSupport
import androidx.core.content.FileProvider
import com.anindra.messages.mms.WspMmsCodec
import com.anindra.messages.mms.store.MmsBox
import java.io.File
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
        var location: String? = null,
        var subscriptionId: Int = -1,
    )

    private val attempts = ConcurrentHashMap<String, Attempt>()

    /**
     * A notification arrived: store it, then fetch everything announced.
     *
     * With the app as default SMS handler nothing else files the announcement,
     * so a push that is not stored here is lost, and the sweep below would find
     * only what an earlier run left behind.
     */
    fun onWapPush(context: Context, data: ByteArray? = null, subscriptionId: Int = -1) {
        if (data != null) announce(context, data, subscriptionId)
        else MmsTrace.w(TAG, "WAP push without a PDU")
        requestPending(context)
    }

    /** Stores an announced message as an inbox row the sweep can pick up. */
    private fun announce(context: Context, data: ByteArray, subscriptionId: Int) {
        val notification = WspMmsCodec().parse(data)
        if (notification == null || !notification.isNotificationInd) {
            MmsTrace.w(TAG, "WAP push is not a readable MMS notification (${data.size} bytes)")
            return
        }
        val location = notification.contentLocation?.trim()
        // The carrier repeats a notification until it is answered, so one that
        // is already waiting must not be filed twice.
        val waiting = runCatching {
            MmsProviderReader(context.contentResolver).pendingDownloads()
                .any { it.contentLocation?.trim() == location }
        }.getOrDefault(false)
        if (waiting) {
            MmsTrace.i(TAG, "MMS notification already waiting, not stored again")
            return
        }
        val stored = MmsFacade.store(context).persist(notification, MmsBox.INBOX, subscriptionId)
        if (stored == null) MmsTrace.w(TAG, "MMS notification could not be stored")
        else MmsTrace.i(TAG, "MMS notification stored as $stored")
    }

    private fun stagingFile(context: Context, rowId: Long) =
        MmsStaging.file(context, MmsStaging.nameFor(rowId))

    /**
     * Turns the PDU the platform wrote for [rowId] into a stored message: the
     * downloaded `m-retrieve-conf` replaces the announcement row. Returns false
     * when there is nothing usable, so the caller can treat it as a failure.
     *
     * [subscriptionId] is the line the announcement was stored under, and the
     * message has to keep it: a retrieved message filed under no line is a
     * different message from the one the carrier delivered to that SIM.
     */
    private fun storeRetrieved(context: Context, rowId: Long, subscriptionId: Int): Boolean {
        val file = stagingFile(context, rowId)
        try {
            val bytes = file.takeIf { it.exists() }?.readBytes()
            if (bytes == null || bytes.isEmpty()) {
                MmsTrace.w(TAG, "MMS $rowId: platform reported success but wrote no PDU")
                return false
            }
            val retrieved = WspMmsCodec().parse(bytes)
            if (retrieved == null) {
                MmsTrace.w(TAG, "MMS $rowId: downloaded PDU is unreadable (${bytes.size} bytes)")
                return false
            }
            val store = MmsFacade.store(context)
            val stored = store.persist(retrieved, MmsBox.INBOX, subscriptionId)
            if (stored == null) {
                MmsTrace.w(TAG, "MMS $rowId: downloaded message could not be stored")
                return false
            }
            // The announcement is superseded by the message itself, and only
            // once the message has a row of its own.
            if (!store.delete(Uri.parse(MmsSupport.messageContentUri(rowId)))) {
                MmsTrace.w(TAG, "MMS $rowId: announcement left behind, message may show twice")
            }
            MmsTrace.i(TAG, "MMS $rowId stored as $stored (${bytes.size} bytes)")
            return true
        } finally {
            file.delete()
        }
    }

    private fun stagingUri(context: Context, rowId: Long): Uri? =
        MmsStaging.uriFor(context, MmsStaging.nameFor(rowId), write = true)

    /** Re-requests any announced MMS still waiting to be fetched, covering a
     *  WAP broadcast that was missed while the app was not the default handler. */
    fun requestPending(context: Context) {
        val rows = try {
            MmsProviderReader(context.contentResolver).pendingDownloads()
        } catch (t: Throwable) {
            MmsTrace.w(TAG, "pending MMS query failed: ${t.message}")
            emptyList()
        }
        // The count is the first thing to check when nothing arrives: a WAP push
        // that produced no pending row never reached the download path at all.
        MmsTrace.i(TAG, "pending MMS notifications: ${rows.size}")
        MmsFacade.diagnostics.pendingSwept(rows.size)
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
            MmsTrace.w(TAG, "announced MMS ${row.id} has no Content-Location to fetch")
            return false
        }
        val key = MmsSupport.messageContentUri(row.id)
        val now = System.currentTimeMillis()
        val attempt = attempts.computeIfAbsent(key) { Attempt() }
        if (!MmsRetry.shouldRetryNow(
                MmsRetry.AUTO_RETRY, attempt.count, attempt.lastAt, now
            )
        ) {
            MmsTrace.i(TAG, "MMS download for $key still backing off (${attempt.count} attempts)")
            // No transfer for this row is in flight, so the staging file left by
            // the last one is dead weight. A row that keeps failing every launch
            // would otherwise leave one behind for as long as the app is installed.
            MmsStaging.release(context, MmsStaging.nameFor(row.id))
            return false
        }
        attempt.count++
        attempt.lastAt = now
        attempt.location = location
        attempt.subscriptionId = row.subscriptionId
        MmsFacade.diagnostics.downloadRequested(row.id, location, attempt.count)
        val name = MmsStaging.nameFor(row.id)
        val destination = stagingUri(context, row.id)
        if (destination == null) {
            attempt.count--
            return false
        }
        return try {
            val completion = PendingIntent.getBroadcast(
                context, key.hashCode(),
                Intent(MmsDownloadReceiver.ACTION_DOWNLOAD_COMPLETE)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_MMS_URI, key)
                    .putExtra(MmsDownloadReceiver.EXTRA_MMS_SUBSCRIPTION, row.subscriptionId)
                    .setComponent(ComponentName(context, MmsDownloadReceiver::class.java)),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            SmsSender.manager(context, row.subscriptionId).downloadMultimediaMessage(
                context, location, destination, null, completion
            )
            // The destination is named on this line because a provider row here is the
            // difference between a download that works and one that fails with
            // MMS_ERROR_IO_ERROR on every attempt, with nothing else to tell.
            MmsTrace.i(
                TAG,
                "MMS download requested for $key from $location (attempt ${attempt.count})" +
                    " into $destination"
            )
            true
        } catch (t: Throwable) {
            MmsTrace.w(TAG, "MMS download request failed for $key: ${t.message}")
            attempt.count--
            MmsStaging.release(context, name)
            false
        }
    }

    /**
     * Applies the result the platform reported for a finished download. A
     * transient failure is retried while the attempt budget lasts; a message the
     * carrier has discarded is forgotten so it stops being reconsidered.
     */
    fun onComplete(
        context: Context,
        uri: String?,
        resultCode: Int,
        httpStatus: Int = 0,
        subscriptionId: Int = -1,
    ) {
        val key = uri ?: return
        val rowId = key.substringAfterLast('/').toLongOrNull() ?: -1L
        // The result code alone cannot separate "the network was down" from "the
        // carrier has thrown this message away": a discarded message reports 404,
        // and retrying it forever re-requests a URL that will never exist.
        if (resultCode != android.app.Activity.RESULT_OK) {
            MmsTrace.w(
                TAG,
                "MMS $key download result code $resultCode, http status $httpStatus " +
                    "-> ${MmsRetry.classify(resultCode, httpStatus)}"
            )
        }
        MmsFacade.diagnostics.downloadCompleted(rowId, resultCode, httpStatus)
        val outcome = MmsRetry.classify(resultCode, httpStatus)
        if (outcome == MmsRetry.SUCCEEDED) {
            attempts.remove(key)
            if (rowId > 0 && !storeRetrieved(context, rowId, subscriptionId)) {
                // Success with nothing readable behind it: leave the announcement
                // in place so it is requested again rather than lost.
                MmsTrace.w(TAG, "MMS $key downloaded but not stored; will be requested again")
            } else if (rowId > 0) {
                MmsStaging.release(context, MmsStaging.nameFor(rowId))
            }
            return
        }
        if (outcome == MmsRetry.NO_RETRY) {
            MmsTrace.w(TAG, "MMS $key permanently failed (code $resultCode), not retrying")
            attempts.remove(key)
            // The row stays for the sweep, but nothing will ask the platform to
            // write here again, so the grant would outlive the transfer.
            if (rowId > 0) MmsStaging.release(context, MmsStaging.nameFor(rowId))
            return
        }
        val attempt = attempts[key] ?: return
        if (outcome == MmsRetry.AUTO_RETRY) {
            val delay = MmsRetry.delayFor(attempt.count)
            MmsTrace.i(TAG, "MMS $key failed (code $resultCode), retrying in ${delay}ms")
            val id = key.substringAfterLast('/').toLongOrNull() ?: return
            scheduleRetry(
                context,
                MmsSupport.PendingDownload(id, attempt.location, attempt.subscriptionId),
                delay,
            )
        } else {
            // Manual retry: the user has to decide, so keep the row eligible but
            // stop the automatic loop.
            MmsTrace.w(TAG, "MMS $key needs manual retry (code $resultCode)")
            attempts.remove(key)
            if (rowId > 0) MmsStaging.release(context, MmsStaging.nameFor(rowId))
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
                .onFailure { MmsTrace.w(TAG, "scheduled retry failed: ${it.message}") }
        }, delayMs)
    }
}
