package com.anindra.messages.sms

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.anindra.messages.data.MmsSupport

/**
 * Stages an outgoing MMS as a `content://mms/outbox/<id>` row, replacing the
 * vendored AOSP `PduPersister` with plain `ContentResolver` writes.
 *
 * The provider answers a rejected insert with a null uri rather than an
 * exception, so every write is null-checked: a half-staged message must never
 * look staged to the caller.
 */
internal object MmsOutbox {

    private const val TAG = "MmsOutbox"
    private const val MMS_CONTENT_TYPE = "application/vnd.wap.mms-message"
    private const val MESSAGE_CLASS_PERSONAL = "P"
    private const val EXPIRY_SECONDS = 604_800L
    private const val SUBSCRIPTION_DEFAULT = -1
    private const val SMIL_SEQ = -1
    private const val MIME_TEXT_PLAIN = "text/plain"

    private val PLMN_SUFFIX = Regex("/TYPE=PLMN$", RegexOption.IGNORE_CASE)

    /**
     * @param parts the SMIL part first, then the attachment, then an optional
     *   caption; the wire bytes live in [MmsPdu.Part.data]
     * @return the new outbox row, or null if any of the writes was refused
     */
    fun persist(
        context: Context,
        transactionId: String,
        dateSeconds: Long,
        to: String,
        parts: List<MmsPdu.Part>,
        subscriptionId: Int
    ): Uri? {
        val recipient = recipient(to)
        if (parts.isEmpty() || recipient.isEmpty()) return null
        return try {
            stage(context, transactionId, dateSeconds, recipient, parts, subscriptionId)
        } catch (t: Throwable) {
            Log.w(TAG, "outbox persist failed: ${t.message}")
            null
        }
    }

    private fun stage(
        context: Context,
        transactionId: String,
        dateSeconds: Long,
        recipient: String,
        parts: List<MmsPdu.Part>,
        subscriptionId: Int
    ): Uri? {
        val resolver = context.contentResolver
        val pdu = ContentValues().apply {
            // An "address" key is never set: the provider reads it to derive a
            // thread id, then still hands it to SQL where "pdu" has no such
            // column, so the insert quietly comes back null.
            put(Telephony.Mms.MESSAGE_TYPE, MmsPdu.MESSAGE_TYPE_SEND_REQ)
            put(
                Telephony.Mms.THREAD_ID,
                Telephony.Threads.getOrCreateThreadId(context, recipient)
            )
            put(Telephony.Mms.DATE, dateSeconds)
            put(Telephony.Mms.MMS_VERSION, MmsPdu.MMS_VERSION_1_2)
            put(Telephony.Mms.MESSAGE_CLASS, MESSAGE_CLASS_PERSONAL)
            put(Telephony.Mms.MESSAGE_SIZE, parts.sumOf { it.data.size.toLong() })
            put(Telephony.Mms.CONTENT_TYPE, MMS_CONTENT_TYPE)
            put(Telephony.Mms.TRANSACTION_ID, transactionId)
            put(Telephony.Mms.EXPIRY, EXPIRY_SECONDS)
            put(Telephony.Mms.PRIORITY, MmsPdu.PRIORITY_NORMAL)
            put(Telephony.Mms.READ_REPORT, MmsPdu.VALUE_NO)
            put(Telephony.Mms.DELIVERY_REPORT, MmsPdu.VALUE_NO)
            // Omitted for -1: from API 30 the provider rejects a subscription
            // that is not on the calling user profile, and the default SMS
            // subscription is the right fallback anyway.
            if (subscriptionId != SUBSCRIPTION_DEFAULT) {
                put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
            }
        }
        val outbox = resolver.insert(Telephony.Mms.Outbox.CONTENT_URI, pdu) ?: return null
        val id = outbox.lastPathSegment?.toLongOrNull() ?: return null
        if (!insertAddress(resolver, id, recipient)) return null
        for (part in parts) {
            if (!insertPart(resolver, id, part)) return null
        }
        return outbox
    }

    private fun insertAddress(resolver: ContentResolver, id: Long, recipient: String): Boolean {
        val values = ContentValues().apply {
            // The provider performs no normalisation at all, so a polluted
            // address here breaks thread matching. sub_id is absent from the
            // addr table before schema v68 and msg_id comes from the uri.
            put(Telephony.Mms.Addr.ADDRESS, recipient)
            put(Telephony.Mms.Addr.TYPE, MmsPdu.TO)
            put(Telephony.Mms.Addr.CHARSET, MmsPdu.CHARSET_UTF_8)
        }
        return resolver.insert(Uri.parse("content://mms/$id/addr"), values) != null
    }

    private fun insertPart(resolver: ContentResolver, id: Long, part: MmsPdu.Part): Boolean {
        val type = MmsSupport.mime(part.contentType)
        val smil = type == MmsPdu.APP_SMIL
        val inline = smil || type == MIME_TEXT_PLAIN
        val values = ContentValues().apply {
            put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
            put(Telephony.Mms.Part.NAME, part.name)
            put(Telephony.Mms.Part.CONTENT_LOCATION, part.name)
            put(Telephony.Mms.Part.CONTENT_ID, part.contentId)
            if (inline) {
                // Inline text has to arrive with the insert: the provider only
                // populates its full-text index then, and it rejects a _data for
                // these two types outright.
                put(Telephony.Mms.Part.TEXT, String(part.data, Charsets.UTF_8))
                if (smil) {
                    put(Telephony.Mms.Part.SEQ, SMIL_SEQ)
                } else {
                    put(Telephony.Mms.Part.CHARSET, part.charset ?: MmsPdu.CHARSET_UTF_8)
                }
            } else {
                put(Telephony.Mms.Part.FILENAME, part.name)
            }
        }
        val stored = resolver.insert(Uri.parse("content://mms/$id/part"), values) ?: return false
        if (inline) return true
        // The provider creates and chmods the backing file itself, from
        // CONTENT_LOCATION, and refuses a caller-supplied _data.
        resolver.openOutputStream(stored)?.use { it.write(part.data) } ?: return false
        return true
    }

    /** Strips the wire-only `/TYPE=PLMN` suffix and any angle brackets. */
    private fun recipient(to: String): String =
        to.trim().replace(PLMN_SUFFIX, "").trim().trim('<', '>').trim()
}
