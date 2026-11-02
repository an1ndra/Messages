package com.anindra.messages.sms

import android.content.Context
import android.net.Uri
import android.util.Log
import com.anindra.messages.MessagesApplication
import com.anindra.messages.data.MmsSupport
import com.anindra.messages.mms.OutgoingAttachment
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

    /** What the app row needs once the stack reports a hand-off outcome. */
    internal enum class HandoffOutcome { NONE, UNLINK, ADOPT }

    /**
     * A refused hand-off deletes the outbox row, so the link has to come out
     * with it; anything the platform accepted may adopt the provider's part.
     */
    internal fun handoffAction(outcome: String, outboxRowId: Long): HandoffOutcome = when {
        outboxRowId <= 0L -> HandoffOutcome.NONE
        outcome == "not_started" -> HandoffOutcome.UNLINK
        else -> HandoffOutcome.ADOPT
    }

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
        messageIds: List<Long>,
        addresses: List<String>,
        media: List<Uri>,
        mimeTypes: List<String>,
        caption: String,
        subscriptionId: Int
    ): Boolean {
        if (messageIds.isEmpty() || media.isEmpty()) return false
        val app = context.applicationContext
        val maxSize = MmsFacade.profiles(app).of(subscriptionId).maxMessageSize()
        MmsTrace.i(
            TAG,
            "sendMms start ids=${messageIds.size} sub=$subscriptionId addrs=${addresses.size} " +
                "parts=${media.size} mimes=$mimeTypes cap=${maxSize}B"
        )
        val attachments = ArrayList<OutgoingAttachment>(media.size)
        for (index in media.indices) {
            val uri = media[index]
            val mimeType = mimeTypes.getOrNull(index).orEmpty().ifBlank {
                MmsSupport.canonicalMime(
                    MmsSupport.defaultAttachmentMime(app.contentResolver.getType(uri), uri.toString())
                )
            }
            val bytes = try {
                app.contentResolver.openInputStream(uri)?.use {
                    AttachmentReader.readBounded(it, readLimit(mimeType, maxSize))
                }
            } catch (t: Throwable) {
                MmsTrace.w(TAG, "could not read attachment $uri", t)
                null
            }
            if (bytes == null) {
                MmsTrace.w(TAG, "MMS not sent: attachment $uri unreadable or over the size cap")
                return false
            }
            attachments.add(OutgoingAttachment(mimeType, bytes))
        }
        val outcome = kotlinx.coroutines.runBlocking {
            MmsFacade.of(
                app,
                ChainedDiagnostics(
                    MmsFacade.diagnostics,
                    Recorder(app, messageIds)
                )
            ).send(
                OutgoingMessage(
                    addresses = addresses,
                    caption = caption,
                    attachments = attachments,
                    subscriptionId = subscriptionId,
                )
            )
        }
        return when (outcome) {
            is SendOutcome.Queued, is SendOutcome.Sent -> {
                MmsTrace.i(TAG, "MMS handed off ids=${messageIds.size} outcome=${outcome.javaClass.simpleName}")
                true
            }
            is SendOutcome.Failed -> {
                MmsTrace.w(
                    TAG,
                    "MMS failed ids=${messageIds.size} result=${outcome.resultCode} http=${outcome.httpStatus}"
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

    /** Links the outbox row to the app messages just before the PDU is handed over. */
    private class Recorder(
        private val context: Context,
        private val messageIds: List<Long>
    ) : MmsDiagnostics {

        @Volatile
        private var outboxRowId = 0L

        override fun sendStarted(transportId: String, subscriptionId: Int, messageUri: String?) {
            val row = messageUri?.let(Uri::parse) ?: return
            // Without this link the provider's sent copy of the message is
            // imported as a second one, so the picture shows twice. Every row of
            // an album shares the provider row, which is what folds them into one
            // bubble.
            row.lastPathSegment?.toLongOrNull()?.let { rowId ->
                outboxRowId = rowId
                val repository = (context.applicationContext as MessagesApplication).repository
                messageIds.forEach { messageId ->
                    runCatching { repository.linkMmsRow(messageId, rowId) }
                        .onFailure { Log.w(TAG, "could not link message $messageId: ${it.message}") }
                }
            }
            val transactionId = MmsPendingSends.transactionIdOf(context, row)
            if (transactionId == null) {
                MmsTrace.w(TAG, "outbox row $row has no transaction id; result cannot be matched")
                return
            }
            MmsPendingSends.remember(context, transactionId, messageIds.first())
            MmsTrace.i(TAG, "outbox row $row linked to ${messageIds.size} message(s)")
        }

        override fun sendCompleted(
            transportId: String,
            outcome: String,
            responseStatus: Int?,
            httpStatus: Int
        ) {
            val rowId = outboxRowId
            val repository = (context.applicationContext as MessagesApplication).repository
            when (handoffAction(outcome, rowId)) {
                HandoffOutcome.NONE -> return
                HandoffOutcome.UNLINK -> {
                    // The stack deletes this outbox row on a refused hand-off. A
                    // link left behind would both hide a later provider row
                    // with the same id and keep the bubble on a dead part URI.
                    messageIds.forEach { messageId ->
                        runCatching { repository.unlinkMmsRow(messageId, rowId) }
                            .onFailure { Log.w(TAG, "could not unlink message $messageId: ${it.message}") }
                    }
                    return
                }
                HandoffOutcome.ADOPT -> {
                    // The picker's (or recorder's) URI is dead after the app is
                    // closed; show the provider's copy of the attachment
                    // instead. This only runs once the platform has taken it.
                    runCatching { repository.adoptProviderMediaRows(rowId, messageIds) }
                        .onFailure { Log.w(TAG, "could not adopt media for $messageIds: ${it.message}") }
                }
            }
        }
    }
}