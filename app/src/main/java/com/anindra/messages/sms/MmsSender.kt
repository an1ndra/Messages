package com.anindra.messages.sms

import android.content.Context
import android.net.Uri
import android.util.Log
import com.anindra.messages.MessagesApplication
import com.anindra.messages.data.MmsSupport
import com.anindra.messages.mms.OutgoingMessage
import com.anindra.messages.mms.SendOutcome
import com.anindra.messages.mms.fit.AttachmentReader
import com.anindra.messages.mms.spi.FitOutcome
import com.anindra.messages.mms.spi.FitRequest
import com.anindra.messages.mms.spi.MmsDiagnostics

/**
 * Sends one MMS through the `:mms` stack.
 *
 * The stack fits the attachment to the carrier, builds the M-Send.req, writes
 * the outbox row and hands the PDU to the platform. What is left to the app is
 * reading the attachment, and remembering which app message the outbox row is
 * for so [SmsStatusReceiver] can settle it when the platform reports back.
 */
internal object MmsSender {
    private const val TAG = "MmsSender"

    /** An image is decoded and re-encoded, so it has to be read whole; this only
     *  stops a runaway source from filling memory. */
    const val IMAGE_READ_CAP_BYTES = 32L * 1024 * 1024

    /**
     * How many bytes of a source are worth reading.
     *
     * An image is shrunk to fit, so its source may be larger than the carrier
     * cap; anything else is sent as it is and therefore cannot be.
     */
    fun readLimit(mimeType: String, carrierMaxMessageSize: Int): Long =
        if (MmsSupport.isImage(mimeType)) IMAGE_READ_CAP_BYTES
        else carrierMaxMessageSize.toLong().coerceAtLeast(0)

    /** @return true when the platform has the message (or the carrier took it). */
    fun send(
        context: Context,
        messageId: Long,
        addresses: List<String>,
        media: Uri,
        mimeType: String,
        caption: String,
        subscriptionId: Int
    ): Boolean {
        val app = context.applicationContext
        val maxSize = MmsFacade.profiles(app).of(subscriptionId).maxMessageSize()
        MmsTrace.i(
            TAG,
            "sendMms start id=$messageId sub=$subscriptionId addrs=${addresses.size} " +
                "media=$media mime=$mimeType cap=${maxSize}B"
        )
        val bytes = try {
            app.contentResolver.openInputStream(media)?.use {
                AttachmentReader.readBounded(it, readLimit(mimeType, maxSize))
            }
        } catch (t: Throwable) {
            MmsTrace.w(TAG, "could not read attachment $media", t)
            null
        }
        if (bytes == null) {
            MmsTrace.w(TAG, "MMS not sent: attachment unreadable or over the size cap")
            return false
        }
        val outcome = kotlinx.coroutines.runBlocking {
            MmsFacade.of(app, ChainedDiagnostics(MmsFacade.diagnostics, Recorder(app, messageId))).send(
                OutgoingMessage(
                    addresses = addresses,
                    caption = caption,
                    attachmentMimeType = mimeType,
                    attachmentBytes = bytes,
                    subscriptionId = subscriptionId,
                )
            )
        }
        return when (outcome) {
            is SendOutcome.Queued, is SendOutcome.Sent -> {
                MmsTrace.i(TAG, "MMS handed off id=$messageId outcome=${outcome.javaClass.simpleName}")
                true
            }
            is SendOutcome.Failed -> {
                MmsTrace.w(
                    TAG,
                    "MMS failed id=$messageId result=${outcome.resultCode} http=${outcome.httpStatus}"
                )
                false
            }
            is SendOutcome.Rejected -> {
                MmsTrace.w(TAG, "MMS not sent: ${outcome.reason}")
                false
            }
        }
    }

    /**
     * The caller's hook and the app-wide recorder, in that order.
     *
     * The recorder is what Diagnostics prints and what logcat mirrors, so a
     * caller that supplies its own hook must not silence it.
     */
    private class ChainedDiagnostics(
        private val first: MmsDiagnostics,
        private val second: MmsDiagnostics
    ) : MmsDiagnostics {
        private fun both(action: (MmsDiagnostics) -> Unit) {
            action(first)
            action(second)
        }

        override fun sendStarted(transportId: String, subscriptionId: Int, messageUri: String?) =
            both { it.sendStarted(transportId, subscriptionId, messageUri) }

        override fun sendBuilt(pduSize: Int, recipientCount: Int) =
            both { it.sendBuilt(pduSize, recipientCount) }

        override fun sendCompleted(
            transportId: String,
            outcome: String,
            responseStatus: Int?,
            httpStatus: Int
        ) = both { it.sendCompleted(transportId, outcome, responseStatus, httpStatus) }

        override fun receiveStarted(messageType: String, subscriptionId: Int) =
            both { it.receiveStarted(messageType, subscriptionId) }

        override fun receiveCompleted(stage: String, messageUri: String?) =
            both { it.receiveCompleted(stage, messageUri) }

        override fun transportSelected(transportId: String, subscriptionId: Int, available: Boolean) =
            both { it.transportSelected(transportId, subscriptionId, available) }

        override fun apnResolved(subscriptionId: Int, mmsc: String?, proxy: String?) =
            both { it.apnResolved(subscriptionId, mmsc, proxy) }

        override fun networkResolved(available: Boolean) =
            both { it.networkResolved(available) }

        override fun attachmentFitted(request: FitRequest, outcome: FitOutcome.Fitted) =
            both { it.attachmentFitted(request, outcome) }

        override fun attachmentRejected(
            mimeType: String,
            sourceBytes: Int,
            budgetBytes: Long,
            reason: String
        ) = both { it.attachmentRejected(mimeType, sourceBytes, budgetBytes, reason) }

        override fun downloadRequested(rowId: Long, contentLocation: String?, attempt: Int) =
            both { it.downloadRequested(rowId, contentLocation, attempt) }

        override fun pendingSwept(count: Int) = both { it.pendingSwept(count) }

        override fun downloadCompleted(rowId: Long, resultCode: Int, httpStatus: Int) =
            both { it.downloadCompleted(rowId, resultCode, httpStatus) }

        override fun noted(tag: String, level: String, message: String) =
            both { it.noted(tag, level, message) }
    }

    /** Links the outbox row to the app message just before the PDU is handed over. */
    private class Recorder(
        private val context: Context,
        private val messageId: Long
    ) : MmsDiagnostics {

        override fun sendStarted(transportId: String, subscriptionId: Int, messageUri: String?) {
            val row = messageUri?.let(Uri::parse) ?: return
            // Without this link the provider's sent copy of the message is
            // imported as a second one, so the picture shows twice.
            row.lastPathSegment?.toLongOrNull()?.let { rowId ->
                runCatching {
                    val repository = (context.applicationContext as MessagesApplication).repository
                    repository.linkMmsRow(messageId, rowId)
                    // The picker's URI is dead after the app is closed; show the
                    // provider's copy of the picture instead.
                    repository.adoptProviderImage(messageId, rowId)
                }.onFailure { Log.w(TAG, "could not link message $messageId: ${it.message}") }
            }
            val transactionId = MmsPendingSends.transactionIdOf(context, row)
            if (transactionId == null) {
                MmsTrace.w(TAG, "outbox row $row has no transaction id; result cannot be matched")
                return
            }
            MmsPendingSends.remember(context, transactionId, messageId)
            MmsTrace.i(TAG, "outbox row $row linked to message $messageId")
        }
    }
}