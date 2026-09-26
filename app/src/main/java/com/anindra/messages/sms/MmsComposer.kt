package com.anindra.messages.sms

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.anindra.messages.data.MmsSupport
import java.io.File
import java.util.UUID

/**
 * Builds and hands off outgoing MMS.
 *
 * `SmsManager.sendMultimediaMessage` does not take the attachment: its URI must
 * point at the MMS *message* to transmit. So a real `m_SendReq` PDU is built by
 * [MmsPdu], a matching row is persisted to the system MMS outbox so the system
 * and the stock messaging UI can see the message, and the composed PDU is served
 * through the app's FileProvider.
 */
internal object MmsComposer {
    private const val TAG = "MmsComposer"
    private const val PDU_TAG = "MmsPdu"
    private const val EXPIRY_SECONDS = 7L * 24 * 60 * 60
    private const val LOG_CHUNK = 512

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
        val attachment = context.contentResolver.openInputStream(media)?.use { it.readBytes() }
        if (attachment == null || attachment.isEmpty()) {
            throw IllegalStateException("attachment unavailable: $media")
        }
        val specs = MmsSupport.outgoingParts(mimeType, caption)
        val transactionId = "T" + java.lang.Long.toHexString(System.currentTimeMillis())
        val dateSeconds = System.currentTimeMillis() / 1000
        val parts = buildParts(specs, caption, attachment)
        val outbox = MmsOutbox.persist(
            context, transactionId, dateSeconds, address, parts, subscriptionId
        ) ?: return null

        val bytes = MmsPdu.sendReq(
            transactionId = transactionId,
            dateSeconds = dateSeconds,
            to = address,
            parts = parts,
            from = ownNumber(context, subscriptionId),
            expirySeconds = EXPIRY_SECONDS
        )
        logPdu(context, transactionId, bytes)

        val file = File(context.cacheDir, "mms-send-${UUID.randomUUID()}.dat")
        file.outputStream().use { it.write(bytes) }
        Prepared(outbox, file, pduContentUri(context, file))
    } catch (t: Throwable) {
        Log.w(TAG, "failed to build mms pdu: ${t.message}")
        null
    }

    /**
     * The SMIL part leads the body: the multipart `start` and `type` parameters
     * are taken from part 0, so without it the root document would be the image.
     */
    private fun buildParts(
        specs: List<MmsSupport.OutgoingPart>,
        caption: String,
        attachment: ByteArray
    ): List<MmsPdu.Part> = buildList {
        add(
            MmsPdu.Part(
                contentType = MmsPdu.APP_SMIL,
                name = "smil.xml",
                contentId = "smil",
                charset = null,
                data = MmsSmil.document(
                    specs[0].name,
                    mediaTag(mimeType = specs[0].mimeType),
                    specs.getOrNull(1)?.name
                ).toByteArray(Charsets.UTF_8)
            )
        )
        add(
            MmsPdu.Part(
                contentType = specs[0].mimeType,
                name = specs[0].name,
                contentId = specs[0].name,
                charset = null,
                data = attachment
            )
        )
        if (specs.size > 1) {
            add(
                MmsPdu.Part(
                    contentType = specs[1].mimeType,
                    name = specs[1].name,
                    contentId = specs[1].name,
                    charset = MmsPdu.CHARSET_UTF_8,
                    data = caption.toByteArray(Charsets.UTF_8)
                )
            )
        }
    }

    private fun mediaTag(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "img"
        mimeType.startsWith("video/") -> "video"
        mimeType.startsWith("audio/") -> "audio"
        else -> "img"
    }

    /**
     * The device's own MSISDN, when it is readable. Reading it needs
     * READ_PHONE_NUMBERS, which the app does not hold, so this is normally null
     * and the PDU carries an insert-address-token for the MMSC to stamp.
     */
    private fun ownNumber(context: Context, subscriptionId: Int): String? = try {
        val manager = context.getSystemService(SubscriptionManager::class.java)
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val active = manager?.activeSubscriptionInfoList
        if (manager == null || telephony == null || active == null) {
            null
        } else {
            active.firstOrNull { it.subscriptionId == subscriptionId }?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    telephony.createForSubscriptionId(subscriptionId)?.line1Number
                } else {
                    telephony.line1Number
                }
            }
        }
    } catch (_: SecurityException) {
        null
    }

    /**
     * Debug builds only: the staged PDU file is deleted by the sent-callback
     * within seconds, so the bytes are echoed here for the regression script.
     */
    private fun logPdu(context: Context, transactionId: String, bytes: ByteArray) {
        val debuggable =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        val hex = bytes.joinToString("") { "%02x".format(it) }
        Log.i(PDU_TAG, "txid=$transactionId n=${bytes.size} i=0 ${hex.take(LOG_CHUNK)}")
        var offset = LOG_CHUNK
        var index = 1
        while (offset < hex.length) {
            val chunk = hex.substring(offset, (offset + LOG_CHUNK).coerceAtMost(hex.length))
            Log.i(PDU_TAG, "txid=$transactionId n=${bytes.size} i=$index $chunk")
            offset += LOG_CHUNK
            index++
        }
    }

    fun pduContentUri(context: Context, file: File): Uri = Uri.Builder()
        .scheme("content")
        .authority(context.packageName + ".fileprovider")
        .appendPath(file.name)
        .build()
}
