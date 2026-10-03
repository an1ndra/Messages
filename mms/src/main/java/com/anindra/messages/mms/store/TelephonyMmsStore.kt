package com.anindra.messages.mms.store

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.BaseColumns
import android.provider.Telephony.Mms.Addr
import android.provider.Telephony.Mms.Part
import android.provider.Telephony.MmsSms.PendingMessages
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.anindra.messages.mms.pdu.CharacterSets
import com.anindra.messages.mms.pdu.ContentTypes
import com.anindra.messages.mms.pdu.EncodedStringValue
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduBody
import com.anindra.messages.mms.pdu.PduHeaders
import com.anindra.messages.mms.pdu.PduPart
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * [MmsStore] over the platform telephony provider.
 *
 * Every provider call goes through [guarded]: the provider throws
 * [SQLiteException] for an unknown column, a uri shape it does not route, or a
 * full database, and none of those should take a transaction down. Only those
 * four exception types are absorbed — anything else is a bug in here.
 */
class TelephonyMmsStore(
    private val resolver: ContentResolver,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Injected so participant derivation can be exercised without a SIM. */
    private val lineOneNumber: () -> String? = { null },
    // The platform comparison, not string equality: a carrier writes this device's
    // own number into To in whatever format the SIM reports, which rarely matches
    // the format the sender typed.
    @Suppress("DEPRECATION")
    private val numbersEqual: (String, String) -> Boolean = { a, b ->
        PhoneNumberUtils.compare(a, b)
    },
) : MmsStore {

    private val probe by lazy { ProviderProbe.forResolver(resolver, MESSAGES_URI) }

    override fun persist(pdu: Pdu, box: MmsBox, subscriptionId: Int): Uri? {
        if (!box.isAddressable) return null
        return persistIn(pdu, box, subscriptionId)
    }

    private fun persistIn(pdu: Pdu, box: MmsBox, subscriptionId: Int): Uri? =
        guarded("persist", null) {
            val values = ContentValues()
            writeHeaders(values, pdu.headers)
            // The header map carries the octet for reading back; the PDU's own field
            // is what this row is, so it is written last and wins.
            values.put(COLUMN_M_TYPE, pdu.messageType)
            values.put(COLUMN_MESSAGE_BOX, box.value)
            if (probe.supportsSubscriptionId() && subscriptionId != INVALID_SUBSCRIPTION_ID) {
                values.put(COLUMN_SUB_ID, subscriptionId)
            }
            threadIdFor(pdu)?.let { values.put(COLUMN_THREAD_ID, it) }

            // Parts are written before the message row. The provider offers no
            // transaction, so the other order lets a concurrent reader see a message
            // whose parts do not exist yet. They go in against a placeholder id and
            // are re-pointed at the real one once it exists.
            val placeholderId = clock()
            var messageSize = 0L
            pdu.body?.parts()?.forEach { part ->
                messageSize += part.data?.size?.toLong() ?: 0L
                persistPart(part, placeholderId)
            }
            if (pdu.headers.longOrNull(HeaderField.MESSAGE_SIZE) == null) {
                values.put(COLUMN_MESSAGE_SIZE, messageSize)
            }

            val inserted = resolver.insert(box.contentUri(), values) ?: return@guarded null
            val messageId = inserted.lastPathSegment?.toLongOrNull() ?: return@guarded null

            // Scoped to the placeholder's own part uri, because a placeholder id can
            // collide with a message id the provider has already handed out.
            val correction = ContentValues(1)
            correction.put(Part.MSG_ID, messageId)
            resolver.update(partsUri(placeholderId), correction, null, null)

            persistAddresses(messageId, pdu)
            inserted
        }

    override fun load(uri: Uri): Pdu? = guarded("load", null) {
        val messageId = uri.lastPathSegment?.toLongOrNull() ?: return@guarded null
        val headers = PduHeaders()
        val messageType = resolver.query(uri, HEADER_PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@guarded null
            readHeaders(cursor, headers)
            headers.octetOrNull(HeaderField.MESSAGE_TYPE)
        } ?: return@guarded null

        Pdu(
            messageType,
            headers.octetOr(HeaderField.MMS_VERSION, HeaderField.CURRENT_MMS_VERSION),
        ).also { pdu ->
            readAddresses(messageId, pdu.headers)
            if (MessageType.hasBody(messageType)) pdu.body = readBody(messageId)
        }
    }

    override fun move(from: Uri, to: MmsBox): Uri? {
        val messageId = from.lastPathSegment?.toLongOrNull() ?: return null
        if (!updateMessageBox(from, to)) return null
        // FAILED and ALL have no collection of their own, so the row keeps the uri
        // it already had; only a real box gets repointed.
        if (!to.isAddressable) return from
        return ContentUris.withAppendedId(to.contentUri(), messageId)
    }

    override fun updateMessageBox(uri: Uri, box: MmsBox): Boolean =
        guarded("updateMessageBox", false) {
            val values = ContentValues(1)
            values.put(COLUMN_MESSAGE_BOX, box.value)
            resolver.update(uri, values, null, null) > 0
        }

    override fun delete(uri: Uri): Boolean = guarded("delete", false) {
        resolver.delete(uri, null, null) > 0
    }

    override fun setRead(uri: Uri, read: Boolean, seen: Boolean): Boolean =
        guarded("setRead", false) {
            val values = ContentValues(2)
            values.put(COLUMN_READ, if (read) 1 else 0)
            values.put(COLUMN_SEEN, if (seen) 1 else 0)
            resolver.update(uri, values, null, null) > 0
        }

    override fun openPartStream(partUri: Uri): InputStream? = guarded("openPartStream", null) {
        try {
            resolver.openInputStream(partUri)
        } catch (e: FileNotFoundException) {
            null
        }
    }

    override fun partBytes(uri: Uri): ByteArray? = openPartStream(uri)?.use { it.readBytes() }

    override fun createThreadId(recipients: List<String>): Long? {
        val numbers = recipients.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (numbers.isEmpty()) return null
        // The provider keys a thread on its participants, so they travel as query
        // parameters; it creates the thread when none matches.
        val uri = THREAD_ID_URI.buildUpon().apply {
            numbers.forEach { appendQueryParameter(RECIPIENT, it) }
        }.build()
        return guarded("createThreadId", null) {
            resolver.query(uri, arrayOf(BaseColumns._ID), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }
    }

    override fun pendingMessages(dueBeforeSeconds: Long): List<PendingMessage> =
        guarded("pendingMessages", emptyList()) {
            val selection =
                "${PendingMessages.ERROR_TYPE} < ? AND ${PendingMessages.DUE_TIME} <= ?"
            val arguments = arrayOf(
                ERR_TYPE_GENERIC_PERMAMENT.toString(),
                dueBeforeSeconds.toString(),
            )
            resolver.query(
                PENDING_URI,
                null,
                selection,
                arguments,
                "${PendingMessages.DUE_TIME} ASC",
            )?.use { cursor ->
                val result = mutableListOf<PendingMessage>()
                while (cursor.moveToNext()) {
                    val rowId = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID))
                    result += PendingMessage(
                        uri = ContentUris.withAppendedId(PENDING_URI, rowId),
                        messageId = cursor.long(PendingMessages.MSG_ID),
                        transactionId = cursor.getString(
                            cursor.getColumnIndexOrThrow(COLUMN_TRANSACTION_ID)
                        ),
                        messageSize = cursor.long(COLUMN_MESSAGE_SIZE),
                        dueTimeSeconds = cursor.long(PendingMessages.DUE_TIME),
                        errorType = cursor.getInt(
                            cursor.getColumnIndexOrThrow(PendingMessages.ERROR_TYPE)
                        ),
                        retryIndex = cursor.getInt(
                            cursor.getColumnIndexOrThrow(PendingMessages.RETRY_INDEX)
                        ),
                    )
                }
                result
            } ?: emptyList()
        }

    override fun setPendingErrorType(uri: Uri, errorType: Int): Boolean =
        guarded("setPendingErrorType", false) {
            val values = ContentValues(1)
            values.put(PendingMessages.ERROR_TYPE, errorType)
            resolver.update(uri, values, null, null) > 0
        }

    override fun deletePending(uri: Uri): Boolean = guarded("deletePending", false) {
        resolver.delete(uri, null, null) > 0
    }

    // --- header <-> column mapping ------------------------------------------------
    //
    // One table, walked ascending by field code, used by both directions. A field
    // mapped here is written and read back; there is no second table to forget.

    private fun writeHeaders(values: ContentValues, headers: PduHeaders) {
        for (column in HEADER_COLUMNS) {
            when (HeaderField.kindOf(column.field)) {
                HeaderField.Kind.ENCODED_STRING_VALUE ->
                    headers.encodedOrNull(column.field)?.let { encoded ->
                        values.put(column.name, encoded.textBytes.toProviderText())
                        column.charset?.let { values.put(it, encoded.charsetMibEnum) }
                    }
                HeaderField.Kind.TEXT_STRING ->
                    headers.textOrNull(column.field)?.let { values.put(column.name, it) }
                HeaderField.Kind.LONG_INTEGER ->
                    headers.longOrNull(column.field)?.let { values.put(column.name, it) }
                HeaderField.Kind.OCTET ->
                    headers.octetOrNull(column.field)?.let { values.put(column.name, it) }
                HeaderField.Kind.MESSAGE_CLASS ->
                    messageClassOctet(headers)?.let { values.put(column.name, it.toString()) }
                HeaderField.Kind.CONTENT_TYPE ->
                    headers.contentTypeOrNull()?.let { values.put(column.name, it) }
                else -> Unit
            }
        }
    }

    private fun readHeaders(cursor: Cursor, headers: PduHeaders) {
        for (column in HEADER_COLUMNS) {
            val index = cursor.getColumnIndex(column.name)
            if (index < 0 || cursor.isNull(index)) continue
            when (HeaderField.kindOf(column.field)) {
                HeaderField.Kind.ENCODED_STRING_VALUE -> {
                    val text = cursor.getString(index) ?: continue
                    val charsetIndex =
                        column.charset?.let { cursor.getColumnIndex(it) } ?: -1
                    headers.setEncoded(
                        column.field,
                        EncodedStringValue(
                            if (charsetIndex >= 0 && !cursor.isNull(charsetIndex)) {
                                cursor.getInt(charsetIndex)
                            } else {
                                CharacterSets.DEFAULT_CHARSET
                            },
                            text.fromProviderText(),
                        ),
                    )
                }
                HeaderField.Kind.TEXT_STRING ->
                    headers.setText(column.field, cursor.getString(index) ?: continue)
                HeaderField.Kind.LONG_INTEGER -> headers.setLong(column.field, cursor.getLong(index))
                HeaderField.Kind.OCTET -> headers.setOctet(column.field, cursor.getInt(index))
                // The class arrives either as the octet or as its token name; the
                // provider stores both in one text column.
                HeaderField.Kind.MESSAGE_CLASS -> {
                    val raw = cursor.getString(index) ?: continue
                    val octet = raw.trim().toIntOrNull()
                    if (octet != null) headers.setMessageClassOctet(octet)
                    else headers.setMessageClassText(raw)
                }
                HeaderField.Kind.CONTENT_TYPE ->
                    headers.setContentType(cursor.getString(index) ?: continue)
                else -> Unit
            }
        }
    }

    /**
     * The `mms` table's class column is text, so the octet form goes in as its
     * decimal string. That is what a provider row written by another app looks
     * like too, which is why it is read back the same way.
     */
    private fun messageClassOctet(headers: PduHeaders): Int? =
        headers.messageClassOctetOrNull() ?: headers.messageClassTextOrNull()
            ?.let { HeaderField.messageClassOctetFor(it) }

    // --- parts ---------------------------------------------------------------------

    private fun persistPart(part: PduPart, messageId: Long) = guarded("persistPart", false) {
        val contentType = repairedContentType(part) ?: return@guarded false
        val values = ContentValues()
        values.put(Part.CONTENT_TYPE, contentType)
        part.charset?.takeIf { it != CharacterSets.ANY_CHARSET }
            ?.let { values.put(Part.CHARSET, it) }
        part.name?.let { values.put(Part.NAME, it) }
        part.contentId?.let { values.put(Part.CONTENT_ID, it) }
        part.contentLocation?.let { values.put(Part.CONTENT_LOCATION, it) }
        part.contentDisposition?.let { values.put(Part.CONTENT_DISPOSITION, it) }
        // Renderers order parts by seq, so SMIL has to come ahead of the start part.
        if (ContentTypes.normalize(contentType) == PduPart.APP_SMIL) {
            values.put(Part.SEQ, SMIL_SEQUENCE)
        }

        val partUri = resolver.insert(partsUri(messageId), values) ?: return@guarded false
        writePartData(partUri, contentType, part.data)
    }

    /**
     * Repairs the two content-type faults carriers actually send. `image/jpg` is
     * not a MIME type, so nothing downstream resolves a decoder for it; a vCard
     * labelled `text/plain` is stored as text and renders as raw vCard source
     * rather than as a contact.
     */
    internal fun repairedContentType(part: PduPart): String? {
        val declared = part.contentType?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (ContentTypes.normalize(declared)) {
            IMAGE_JPG -> IMAGE_JPEG
            TEXT_PLAIN ->
                if (part.data?.startsWithBytes(VCARD_MAGIC) == true) TEXT_X_VCARD else declared
            else -> declared
        }
    }

    /**
     * Text bodies live in the part's `text` column, where the provider makes them
     * searchable. Everything else streams — an attachment does not belong in a
     * column, and reading it in to get there is how a photo takes the heap down.
     */
    private fun writePartData(partUri: Uri, contentType: String, data: ByteArray?): Boolean =
        guarded("writePartData", false) {
            val bytes = data ?: EMPTY_BYTES
            if (ContentTypes.isText(contentType) || ContentTypes.normalize(contentType) == PduPart.APP_SMIL) {
                val values = ContentValues(1)
                values.put(Part.TEXT, bytes.toProviderText())
                resolver.update(partUri, values, null, null) > 0
            } else {
                val out = resolver.openOutputStream(partUri) ?: return@guarded false
                out.use { it.write(bytes) }
                true
            }
        }

    private fun readBody(messageId: Long): PduBody? = guarded("readBody", null) {
        val cursor = resolver.query(
            partsUri(messageId), PART_PROJECTION, null, null, PART_SORT_ORDER,
        ) ?: return@guarded null
        cursor.use {
            if (cursor.count == 0) return@guarded null
            val body = PduBody()
            while (cursor.moveToNext()) {
                val contentType =
                    cursor.getString(cursor.getColumnIndexOrThrow(Part.CONTENT_TYPE)) ?: continue
                body.add(
                    PduPart().also { part ->
                        part.contentType = contentType
                        part.name = cursor.getString(cursor.getColumnIndexOrThrow(Part.NAME))
                        part.contentId =
                            cursor.getString(cursor.getColumnIndexOrThrow(Part.CONTENT_ID))
                        part.contentLocation =
                            cursor.getString(cursor.getColumnIndexOrThrow(Part.CONTENT_LOCATION))
                        part.contentDisposition = cursor.getString(
                            cursor.getColumnIndexOrThrow(Part.CONTENT_DISPOSITION)
                        )
                        part.charset = cursor.nullableInt(Part.CHARSET)
                        part.data = readPartData(cursor, contentType)
                    },
                )
            }
            body
        }
    }

    private fun readPartData(cursor: Cursor, contentType: String): ByteArray {
        val textIndex = cursor.getColumnIndexOrThrow(Part.TEXT)
        if (!cursor.isNull(textIndex)) return cursor.getString(textIndex)!!.fromProviderText()
        val partId = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID))
        return openPartStream(ContentUris.withAppendedId(PART_CONTENT_URI, partId))
            ?.use { it.readBytes() } ?: EMPTY_BYTES
    }

    // --- addresses and threads ----------------------------------------------------

    /**
     * Only the three types that become a conversation get one. Allocating a thread
     * for an acknowledgement would leave an empty conversation behind it.
     */
    private fun threadIdFor(pdu: Pdu): Long? = when (pdu.messageType) {
        MessageType.SEND_REQ ->
            createThreadId(pdu.to.mapNotNull { it.text.takeIf(String::isNotBlank) })
        MessageType.NOTIFICATION_IND, MessageType.RETRIEVE_CONF -> createThreadId(participantsOf(pdu))
        else -> null
    }

    /**
     * An inbound message belongs to its sender plus everyone else it was addressed
     * to. This device's own number sits in To because the carrier fills it in, and
     * leaving it in files the message under a thread that contains ourselves.
     */
    private fun participantsOf(pdu: Pdu): List<String> {
        val mine = lineOneNumber()
        val addressed = (pdu.to + pdu.cc).mapNotNull { it.text.takeIf(String::isNotBlank) }
        val participants = mutableListOf<String>()
        pdu.from?.text?.takeIf { it.isNotBlank() }?.let { participants += it }
        addressed.forEach { number ->
            val ours = if (mine == null) addressed.size == 1 else numbersEqual(number, mine)
            if (!ours) participants += number
        }
        return participants.distinct()
    }

    private fun persistAddresses(messageId: Long, pdu: Pdu) = guarded("persistAddresses", true) {
        val uri = addressesUri(messageId)
        pdu.from?.let { insertAddress(uri, HeaderField.FROM, it) }
        listOf(
            HeaderField.TO to pdu.to,
            HeaderField.CC to pdu.cc,
            HeaderField.BCC to pdu.bcc,
        ).forEach { (type, values) -> values.forEach { insertAddress(uri, type, it) } }
        true
    }

    private fun insertAddress(uri: Uri, type: Int, value: EncodedStringValue) {
        val values = ContentValues(3)
        values.put(Addr.ADDRESS, value.textBytes.toProviderText())
        values.put(Addr.CHARSET, value.charsetMibEnum)
        values.put(Addr.TYPE, type)
        resolver.insert(uri, values)
    }

    private fun readAddresses(messageId: Long, headers: PduHeaders) =
        guarded("readAddresses", null) {
            val cursor = resolver.query(
                addressesUri(messageId),
                arrayOf(Addr.ADDRESS, Addr.CHARSET, Addr.TYPE),
                null,
                null,
                null,
            ) ?: return@guarded null
            cursor.use {
                while (cursor.moveToNext()) {
                    val address = cursor.getString(0) ?: continue
                    if (address.isBlank()) continue
                    val charset = cursor.getInt(1)
                    val value = EncodedStringValue(
                        charset.takeIf { it != 0 } ?: CharacterSets.DEFAULT_CHARSET,
                        address.fromProviderText(),
                    )
                    when (val type = cursor.getInt(2)) {
                        HeaderField.FROM -> headers.setFrom(value)
                        HeaderField.TO, HeaderField.CC, HeaderField.BCC ->
                            headers.addEncoded(type, value)
                        else -> Log.w(TAG, "ignoring address row of unknown type $type")
                    }
                }
            }
            null
        }

    // --- helpers ------------------------------------------------------------------

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun Cursor.nullableInt(column: String): Int? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) }

    private fun MmsBox.contentUri(): Uri {
        val segment = path ?: throw IllegalArgumentException("$name has no collection uri")
        return Uri.withAppendedPath(MESSAGES_URI, segment)
    }

    private fun partsUri(messageId: Long): Uri =
        Uri.withAppendedPath(Uri.withAppendedPath(MESSAGES_URI, messageId.toString()), PART_SEGMENT)

    private fun addressesUri(messageId: Long): Uri =
        Uri.withAppendedPath(Uri.withAppendedPath(MESSAGES_URI, messageId.toString()), ADDR_SEGMENT)

    private inline fun <T> guarded(what: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (e: SQLiteException) {
        Log.w(TAG, "$what failed: ${e.message}", e)
        fallback
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "$what failed: ${e.message}", e)
        fallback
    } catch (e: SecurityException) {
        Log.w(TAG, "$what failed: ${e.message}", e)
        fallback
    } catch (e: NullPointerException) {
        Log.w(TAG, "$what failed: ${e.message}", e)
        fallback
    }

    private class HeaderColumn(val field: Int, val name: String, val charset: String? = null)

    companion object {
        private const val TAG = "MmsStore"

        private const val PART_SEGMENT = "part"
        private const val ADDR_SEGMENT = "addr"
        private const val PART_SORT_ORDER = "seq ASC, _id ASC"
        private const val RECIPIENT = "recipient"

        private val EMPTY_BYTES = ByteArray(0)

        private const val INVALID_SUBSCRIPTION_ID = -1
        private const val ERR_TYPE_GENERIC_PERMAMENT = 128

        /** Pushes SMIL ahead of the part it starts, because the sort is on seq. */
        private const val SMIL_SEQUENCE = -1

        private const val IMAGE_JPG = "image/jpg"
        private const val IMAGE_JPEG = "image/jpeg"
        private const val TEXT_PLAIN = "text/plain"
        private const val TEXT_X_VCARD = "text/x-vCard"
        private val VCARD_MAGIC = "BEGIN:VCARD".toByteArray(StandardCharsets.US_ASCII)

        private val MESSAGES_URI = Uri.parse(MESSAGES_AUTHORITY)
        private val PENDING_URI = Uri.parse("content://mms-sms/pending")
        private val THREAD_ID_URI = Uri.parse("content://mms-sms/threadID")
        private val PART_CONTENT_URI = Uri.parse("content://mms/part")

        /**
         * The `mms` table's columns and box values are hidden API, so the names the
         * provider documents are restated here. They match what the app's existing
         * provider reader projects.
         */
        private const val MESSAGES_AUTHORITY = "content://mms"
        private const val COLUMN_MESSAGE_BOX = "msg_box"
        private const val COLUMN_THREAD_ID = "thread_id"
        private const val COLUMN_M_TYPE = "m_type"
        private const val COLUMN_MMS_VERSION = "mms_version"
        private const val COLUMN_MESSAGE_SIZE = "m_size"
        private const val COLUMN_TRANSACTION_ID = "transaction_id"
        private const val COLUMN_CONTENT_TYPE = "ct_t"
        private const val COLUMN_MESSAGE_CLASS = "m_class"
        private const val COLUMN_CONTENT_LOCATION = "ctt_s"
        private const val COLUMN_MESSAGE_ID = "m_id"
        private const val COLUMN_RESPONSE_TEXT = "resp_text"
        private const val COLUMN_CONTENT_CLASS = "c_cls"
        private const val COLUMN_DELIVERY_REPORT = "d_report"
        private const val COLUMN_PRIORITY = "pri"
        private const val COLUMN_READ_REPORT = "r_report"
        private const val COLUMN_READ_STATUS = "read_status"
        private const val COLUMN_REPORT_ALLOWED = "r_allowed"
        private const val COLUMN_RETRIEVE_STATUS = "ret_status"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_DATE = "date"
        private const val COLUMN_DELIVERY_TIME = "d_tm"
        private const val COLUMN_EXPIRY = "expiry"
        private const val COLUMN_SUBJECT = "sub"
        private const val COLUMN_SUBJECT_CHARSET = "sub_cs"
        private const val COLUMN_RETRIEVE_TEXT = "rt_text"
        private const val COLUMN_RETRIEVE_TEXT_CHARSET = "rt_tok"
        private const val COLUMN_READ = "read"
        private const val COLUMN_SEEN = "seen"
        private const val COLUMN_SUB_ID = "sub_id"

        private val HEADER_COLUMNS = listOf(
            HeaderColumn(
                HeaderField.RETRIEVE_TEXT, COLUMN_RETRIEVE_TEXT, COLUMN_RETRIEVE_TEXT_CHARSET,
            ),
            HeaderColumn(HeaderField.SUBJECT, COLUMN_SUBJECT, COLUMN_SUBJECT_CHARSET),
            HeaderColumn(HeaderField.CONTENT_LOCATION, COLUMN_CONTENT_LOCATION),
            HeaderColumn(HeaderField.CONTENT_TYPE, COLUMN_CONTENT_TYPE),
            HeaderColumn(HeaderField.MESSAGE_CLASS, COLUMN_MESSAGE_CLASS),
            HeaderColumn(HeaderField.MESSAGE_ID, COLUMN_MESSAGE_ID),
            HeaderColumn(HeaderField.RESPONSE_TEXT, COLUMN_RESPONSE_TEXT),
            HeaderColumn(HeaderField.TRANSACTION_ID, COLUMN_TRANSACTION_ID),
            HeaderColumn(HeaderField.CONTENT_CLASS, COLUMN_CONTENT_CLASS),
            HeaderColumn(HeaderField.DELIVERY_REPORT, COLUMN_DELIVERY_REPORT),
            HeaderColumn(HeaderField.MESSAGE_TYPE, COLUMN_M_TYPE),
            HeaderColumn(HeaderField.MMS_VERSION, COLUMN_MMS_VERSION),
            HeaderColumn(HeaderField.PRIORITY, COLUMN_PRIORITY),
            HeaderColumn(HeaderField.READ_REPORT, COLUMN_READ_REPORT),
            HeaderColumn(HeaderField.READ_STATUS, COLUMN_READ_STATUS),
            HeaderColumn(HeaderField.REPORT_ALLOWED, COLUMN_REPORT_ALLOWED),
            HeaderColumn(HeaderField.RETRIEVE_STATUS, COLUMN_RETRIEVE_STATUS),
            HeaderColumn(HeaderField.STATUS, COLUMN_STATUS),
            HeaderColumn(HeaderField.DATE, COLUMN_DATE),
            HeaderColumn(HeaderField.DELIVERY_TIME, COLUMN_DELIVERY_TIME),
            HeaderColumn(HeaderField.EXPIRY, COLUMN_EXPIRY),
            HeaderColumn(HeaderField.MESSAGE_SIZE, COLUMN_MESSAGE_SIZE),
        )

        private val HEADER_PROJECTION = arrayOf(
            BaseColumns._ID,
            *HEADER_COLUMNS.flatMap { listOfNotNull(it.name, it.charset) }
                .distinct()
                .toTypedArray(),
        )

        private val PART_PROJECTION = arrayOf(
            BaseColumns._ID,
            Part.CHARSET,
            Part.CONTENT_DISPOSITION,
            Part.CONTENT_ID,
            Part.CONTENT_LOCATION,
            Part.CONTENT_TYPE,
            Part.NAME,
            Part.TEXT,
        )

        /**
         * The provider's TEXT columns are ISO-8859-1, which maps bytes 0..255 to
         * the same code points, so a string form round-trips the bytes unchanged.
         */
        private fun ByteArray.toProviderText(): String = String(this, StandardCharsets.ISO_8859_1)

        private fun String.fromProviderText(): ByteArray =
            toByteArray(StandardCharsets.ISO_8859_1)

        private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}
