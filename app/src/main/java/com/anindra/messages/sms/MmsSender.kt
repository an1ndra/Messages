package com.anindra.messages.sms

import android.content.Context
import android.net.Uri
import android.util.Log
import com.anindra.messages.MessagesApplication
import com.anindra.messages.data.MmsSupport
import com.anindra.messages.mms.OutgoingMessage
import com.anindra.messages.mms.SendOutcome
import com.anindra.messages.mms.fit.AttachmentReader
import com.anindra.messages.mms.spi.MmsDiagnostics
import kotlinx.coroutines.runBlocking

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

    /** How many bytes of a source are worth reading: an image is shrunk to fit,
     *  so its source may be larger than the carrier cap, anything else is sent
     *  as it is and cannot be. */
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
        val bytes = try {
            app.contentResolver.openInputStream(media)?.use {
                AttachmentReader.readBounded(it, readLimit(mimeType, maxSize))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "could not read attachment $media: ${t.message}")
            null
        }
        if (bytes == null) {
            Log.w(TAG, "MMS not sent: attachment unreadable or over the size cap")
            return false
        }
        val outcome = runBlocking {
            MmsFacade.of(app, Recorder(app, messageId)).send(
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
            is SendOutcome.Queued, is SendOutcome.Sent -> true
            is SendOutcome.Failed -> {
                Log.w(TAG, "MMS failed: result ${outcome.resultCode}, http ${outcome.httpStatus}")
                false
            }
            is SendOutcome.Rejected -> {
                Log.w(TAG, "MMS not sent: ${outcome.reason}")
                false
            }
        }
    }

    /** Links the outbox row to the app message just before the PDU is handed over. */
    private class Recorder(private val context: Context, private val messageId: Long) :
        MmsDiagnostics {
        override fun sendStarted(transportId: String, subscriptionId: Int, messageUri: String?) {
            val row = messageUri?.let(Uri::parse) ?: return
            // Without this link the provider's sent copy of the message is
            // imported as a second one, so the picture shows twice.
            row.lastPathSegment?.toLongOrNull()?.let { rowId ->
                runCatching {
                    (context.applicationContext as MessagesApplication)
                        .repository.linkMmsRow(messageId, rowId)
                }.onFailure { Log.w(TAG, "could not link message $messageId: ${it.message}") }
            }
            val transactionId = MmsPendingSends.transactionIdOf(context, row)
            if (transactionId == null) {
                Log.w(TAG, "outbox row $row has no transaction id; result cannot be matched")
                return
            }
            MmsPendingSends.remember(context, transactionId, messageId)
        }
    }
}
