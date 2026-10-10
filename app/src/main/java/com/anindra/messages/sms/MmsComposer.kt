package com.anindra.messages.sms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Telephony
import com.android.mms.dom.smil.parser.SmilXmlSerializer
import com.anindra.messages.data.MmsConfig
import com.anindra.messages.data.MmsImageSizing
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
import java.io.InputStream
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
 *
 * Size, image dimensions and the report headers all come from the carrier
 * config, so an attachment is fitted to the carrier before it is handed over
 * instead of being rejected by the network.
 */
internal object MmsComposer {
    private const val TAG = "MmsComposer"
    private const val EXPIRY_SECONDS = 7L * 24 * 60 * 60

    /** Headroom for the SMIL, text and header parts, so an attachment alone never
     *  fills the carrier budget and pushes the assembled PDU over the limit. */
    const val PDU_OVERHEAD_BYTES = 12 * 1024

    private val JPEG_QUALITIES = intArrayOf(90, 75, 60, 45)

    data class Prepared(val outboxUri: Uri, val pduFile: File, val pduUri: Uri, val size: Int)

    enum class Reason { ATTACHMENT_UNREADABLE, TOO_LARGE }

    sealed interface Outcome {
        data class Ready(val prepared: Prepared) : Outcome
        data class Rejected(val reason: Reason) : Outcome
    }

    private class Attachment(val mimeType: String, val bytes: ByteArray)

    /** An attachment that is empty is unreadable; one that busts the carrier
     *  budget is a size failure, and the two need different handling. */
    private class EncodeFailure(val reason: Reason) : RuntimeException()

    fun prepare(
        context: Context,
        addresses: List<String>,
        media: Uri,
        mimeType: String,
        caption: String,
        subscriptionId: Int,
        config: MmsConfig
    ): Outcome {
        val budget = (config.maxMessageSize - PDU_OVERHEAD_BYTES).coerceAtLeast(0).toLong()

        val attachment = try {
            encodeAttachment(context, media, mimeType, config, budget)
        } catch (f: EncodeFailure) {
            return Outcome.Rejected(f.reason)
        } catch (t: Throwable) {
            MmsTrace.w(TAG, "could not read attachment $media: ${t.message}")
            return Outcome.Rejected(Reason.ATTACHMENT_UNREADABLE)
        } ?: return Outcome.Rejected(Reason.ATTACHMENT_UNREADABLE)

        return try {
            val request = buildRequest(
                context, addresses, attachment, caption, subscriptionId, config
            )
            val outbox = PduPersister.getPduPersister(context).persist(
                request, Telephony.Mms.Outbox.CONTENT_URI, true, true, null, subscriptionId
            )
            if (outbox.lastPathSegment?.toLongOrNull() == null) {
                MmsTrace.w(TAG, "outbox persist returned $outbox")
                return Outcome.Rejected(Reason.ATTACHMENT_UNREADABLE)
            }
            // Re-read so the transmitted bytes match the stored row. The
            // provider has no app_id column on modern releases, so the app-side
            // id travels in the sent PendingIntent instead.
            val bytes = PduComposer(context, PduPersister.getPduPersister(context).load(outbox)).make()
            if (!config.acceptsPayload(bytes.size.toLong())) {
                MmsTrace.w(TAG, "composed PDU is ${bytes.size}B over carrier cap ${config.maxMessageSize}B")
                context.contentResolver.delete(outbox, null, null)
                return Outcome.Rejected(Reason.TOO_LARGE)
            }
            val file = File(context.cacheDir, "mms-send-${UUID.randomUUID()}.dat")
            file.outputStream().use { it.write(bytes) }
            val prepared = Prepared(outbox, file, pduContentUri(context, file), bytes.size)
            MmsTrace.i(TAG, "composed ${bytes.size}B PDU at ${prepared.pduUri}")
            Outcome.Ready(prepared)
        } catch (t: Throwable) {
            MmsTrace.w(TAG, "failed to build mms pdu: ${t.message}")
            Outcome.Rejected(Reason.ATTACHMENT_UNREADABLE)
        }
    }

    /**
     * Fits the attachment to the carrier's budget. Images are downscaled to the
     * advertised dimensions and re-encoded; anything else is streamed through
     * unchanged but still bounded, so a large video can no longer exhaust memory.
     */
    private fun encodeAttachment(
        context: Context,
        media: Uri,
        mimeType: String,
        config: MmsConfig,
        budget: Long
    ): Attachment? {
        if (!MmsSupport.isImage(mimeType)) {
            val input = openMedia(context, media) ?: return null
            val bytes = input.use { readBounded(it, budget) } ?: return null
            return Attachment(mimeType, bytes)
        }
        return encodeImage(context, media, config, budget)
    }

    private fun encodeImage(
        context: Context,
        media: Uri,
        config: MmsConfig,
        budget: Long
    ): Attachment? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream returns null by design with inJustDecodeBounds, so the
        // stream is what gets null-checked and the size comes back via [bounds].
        val opened = openMedia(context, media) ?: return null
        opened.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val target = MmsImageSizing.fitWithin(
            bounds.outWidth, bounds.outHeight, config.maxImageWidth, config.maxImageHeight
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = MmsImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, target)
        }
        val decoded = openMedia(context, media)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        // Subsampling only lands on powers of two, so it cannot by itself hit the
        // cap (900x600 at 8x decodes to 113x75, not 100x67). The exact resize to
        // [target] has to happen explicitly.
        val scaled = target?.let { decoded.scaledTo(it) } ?: decoded
        if (scaled !== decoded) decoded.recycle()
        return encodeWithinBudget(scaled, bounds.outWidth, bounds.outHeight, config, budget)
    }

    /**
     * Re-encodes at descending quality until the result fits [budget].
     * @throws EncodeFailure when even the lowest quality is over the cap.
     */
    private fun encodeWithinBudget(
        scaled: Bitmap,
        sourceWidth: Int,
        sourceHeight: Int,
        config: MmsConfig,
        budget: Long
    ): Attachment {
        return try {
            for (quality in JPEG_QUALITIES) {
                val out = ByteArrayOutputStream()
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw EncodeFailure(Reason.ATTACHMENT_UNREADABLE)
                }
                if (out.size().toLong() <= budget) {
                    // Downscaling may have turned a PNG or WebP into JPEG, so the
                    // part has to be typed from the bytes actually being sent.
                    MmsTrace.i(
                        TAG,
                        "attachment ${sourceWidth}x${sourceHeight} -> " +
                            "${scaled.width}x${scaled.height} " +
                            "(carrier cap ${config.maxImageWidth}x${config.maxImageHeight}), " +
                            "${out.size()}B at q$quality"
                    )
                    return Attachment("image/jpeg", out.toByteArray())
                }
            }
            // Even the lowest quality busts the carrier budget: downscaling and
            // re-encoding both ran out of room, so this is a size failure.
            throw EncodeFailure(Reason.TOO_LARGE)
        } finally {
            scaled.recycle()
        }
    }

    /** Exact resize to [target]; the source is recycled by the caller. */
    private fun Bitmap.scaledTo(target: MmsImageSizing.Size): Bitmap {
        if (width == target.width && height == target.height) return this
        return Bitmap.createScaledBitmap(this, target.width, target.height, true)
    }

    /**
     * Streams at most [limit] bytes so an oversized attachment is never fully
     * buffered. @return null when the stream is empty.
     * @throws EncodeFailure when the media is larger than the carrier allows.
     */
    private fun readBounded(input: InputStream, limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw EncodeFailure(Reason.TOO_LARGE)
            out.write(buffer, 0, count)
        }
        return if (out.size() == 0) null else out.toByteArray()
    }

    private fun openMedia(context: Context, media: Uri): InputStream? = try {
        context.contentResolver.openInputStream(media)
    } catch (t: Throwable) {
        MmsTrace.w(TAG, "openInputStream($media) failed: ${t.message}")
        null
    }

    private fun buildRequest(
        context: Context,
        addresses: List<String>,
        attachment: Attachment,
        caption: String,
        subscriptionId: Int,
        config: MmsConfig
    ): SendReq {
        val request = SendReq()
        request.prepareFromAddress(context, "", subscriptionId)
        // One To header entry per recipient: this is how a group MMS is addressed.
        // Blank entries are dropped rather than sent as an empty address.
        addresses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .forEach { request.addTo(EncodedStringValue(it)) }
        request.date = System.currentTimeMillis() / 1000

        val body = PduBody()
        val parts = MmsSupport.outgoingParts(attachment.mimeType, caption)
        addPart(body, parts[0].name, attachment.mimeType, attachment.bytes)
        var size = attachment.bytes.size.toLong()
        if (parts.size > 1) {
            val text = caption.toByteArray()
            addPart(body, parts[1].name, parts[1].mimeType, text)
            size += text.size
        }
        addSmil(body)
        request.body = body
        request.messageSize = size
        request.messageClass = PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray()
        request.expiry = EXPIRY_SECONDS
        request.priority = PduHeaders.PRIORITY_NORMAL
        request.deliveryReport = config.deliveryReportHeader()
        request.readReport = config.readReportHeader()
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

    private fun addPart(body: PduBody, name: String, mimeType: String, data: ByteArray) {
        body.addPart(PduPart().apply {
            contentType = mimeType.toByteArray()
            contentLocation = name.toByteArray()
            contentId = name.toByteArray()
            if (mimeType.startsWith("text")) charset = CharacterSets.UTF_8
            this.data = data
        })
    }

    fun pduContentUri(context: Context, file: File): Uri =
        androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
}
