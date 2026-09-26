package com.anindra.messages.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.android.mms.dom.smil.parser.SmilXmlSerializer
import com.anindra.messages.data.MmsSupport
import com.google.android.mms.ContentType
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.pdu_alt.SendReq
import com.google.android.mms.smil.SmilHelper
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Builds and hands off outgoing MMS.
 *
 * `SmsManager.sendMultimediaMessage` does not take the attachment: its URI must
 * point at the MMS *message* to transmit. So a real `m_SendReq` PDU is built,
 * persisted as a `content://mms/outbox/<id>` row (which also gives the system a
 * provider-side record), then composed to the binary PDU the radio expects and
 * served through the app's FileProvider. Mirrors the flow used by
 * quik/Fossify (klinker `Transaction.sendMmsThroughSystem`).
 */
internal object MmsComposer {
    private const val TAG = "MmsComposer"
    private const val EXPIRY_SECONDS = 7L * 24 * 60 * 60

    data class Prepared(val outboxUri: Uri, val pduFile: File, val pduUri: Uri)

    /**
     * @return the outbox row plus the composed PDU to transmit, or null on failure
     */
    fun prepare(
        context: Context,
        address: String,
        media: Uri,
        mimeType: String,
        caption: String,
        subscriptionId: Int
    ): Prepared? = try {
        val request = buildRequest(context, address, media, mimeType, caption, subscriptionId)
        val outbox = PduPersister.getPduPersister(context).persist(
            request, Telephony.Mms.Outbox.CONTENT_URI, true, true, null, subscriptionId
        )
        if (outbox.lastPathSegment?.toLongOrNull() == null) {
            Log.w(TAG, "outbox persist returned $outbox")
            null
        } else {
            // Re-read so the transmitted bytes match the stored row. The
            // provider has no app_id column on modern releases, so the app-side
            // id travels in the sent PendingIntent instead.
            val bytes = PduComposer(context, PduPersister.getPduPersister(context).load(outbox)).make()
            val file = File(context.cacheDir, "mms-send-${UUID.randomUUID()}.dat")
            file.outputStream().use { it.write(bytes) }
            Prepared(outbox, file, pduContentUri(context, file))
        }
    } catch (t: Throwable) {
        Log.w(TAG, "failed to build mms pdu: ${t.message}")
        null
    }

    private fun buildRequest(
        context: Context,
        address: String,
        media: Uri,
        mimeType: String,
        caption: String,
        subscriptionId: Int
    ): SendReq {
        val request = SendReq()
        request.prepareFromAddress(context, "", subscriptionId)
        request.addTo(EncodedStringValue(address))
        request.date = System.currentTimeMillis() / 1000

        val body = PduBody()
        val attachment = context.contentResolver.openInputStream(media)?.use { it.readBytes() }
        if (attachment == null || attachment.isEmpty()) {
            throw IllegalStateException("attachment unavailable: $media")
        }
        val parts = MmsSupport.outgoingParts(mimeType, caption)
        val text = caption.toByteArray()
        addPart(body, parts[0].mimeType, parts[0].name, attachment)
        var size = attachment.size.toLong()
        if (parts.size > 1) {
            addPart(body, parts[1].mimeType, parts[1].name, text)
            size += text.size
        }
        addSmil(body)
        request.body = body
        request.messageSize = size
        request.messageClass = PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray()
        request.expiry = EXPIRY_SECONDS
        request.priority = PduHeaders.PRIORITY_NORMAL
        request.deliveryReport = PduHeaders.VALUE_NO
        request.readReport = PduHeaders.VALUE_NO
        return request
    }

    /** A SMIL part is expected by many carriers and clients, as in quik. */
    private fun addSmil(body: PduBody) {
        val out = ByteArrayOutputStream()
        SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(body), out)
        body.addPart(0, PduPart().apply {
            contentId = "smil".toByteArray()
            contentLocation = "smil.xml".toByteArray()
            contentType = ContentType.APP_SMIL.toByteArray()
            data = out.toByteArray()
        })
    }

    private fun addPart(body: PduBody, mimeType: String, name: String, data: ByteArray) {
        body.addPart(PduPart().apply {
            contentType = mimeType.toByteArray()
            contentLocation = name.toByteArray()
            contentId = name.toByteArray()
            if (mimeType.startsWith("text")) charset = CharacterSets.UTF_8
            this.data = data
        })
    }

    fun pduContentUri(context: Context, file: File): Uri = Uri.Builder()
        .scheme("content")
        .authority(context.packageName + ".fileprovider")
        .appendPath(file.name)
        .build()
}
