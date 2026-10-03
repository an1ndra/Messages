package com.anindra.messages.mms.pdu

/**
 * One part of a multipart body.
 *
 * Data is held as bytes rather than a stream because the PDU layer is pure and
 * the sizes involved are already bounded by the carrier's message cap before a
 * part reaches here.
 */
class PduPart {
    var contentType: String? = null
    var name: String? = null
    var contentId: String? = null
    var contentLocation: String? = null
    var charset: Int? = null
    var contentDisposition: String? = null
    var transferEncoding: String? = null
    var data: ByteArray? = null

    /** SMIL sorts first when a renderer orders parts by sequence. */
    var sequence: Int? = null

    val isSmil: Boolean
        get() = contentType?.let { ContentTypes.normalize(it) } == APP_SMIL

    val isTextual: Boolean
        get() = contentType?.let { ContentTypes.isText(it) } == true

    /**
     * The name a part should be persisted and referenced under. Carriers do send
     * a SMIL part with no name at all, and a part with no name cannot be
     * forwarded later because composing it would fail.
     */
    fun effectiveName(): String {
        name?.takeIf { it.isNotBlank() }?.let { return it }
        contentLocation?.takeIf { it.isNotBlank() }?.let { return it.substringAfterLast('/') }
        contentId?.takeIf { it.isNotBlank() }?.let { return it.removeSurrounding("<", ">") }
        return if (isSmil) DEFAULT_SMIL_NAME else "part"
    }

    fun contentIdOrNull(): String? = contentId?.trim('<', '>')?.takeIf { it.isNotBlank() }

    fun copy(): PduPart = PduPart().also {
        it.contentType = contentType
        it.name = name
        it.contentId = contentId
        it.contentLocation = contentLocation
        it.charset = charset
        it.contentDisposition = contentDisposition
        it.transferEncoding = transferEncoding
        it.data = data
        it.sequence = sequence
    }

    companion object {
        const val APP_SMIL = "application/smil"
        const val DEFAULT_SMIL_NAME = "smil.xml"

        const val CHARSET = 0x81
        const val CONTENT_LOCATION = 0x8E
        const val CONTENT_ID = 0xC0
        const val CONTENT_DISPOSITION = 0xC1
        const val NAME = 0x85
        const val TYPE = 0x83
        const val FILENAME = 0x89
        const val BASE64 = "base64"
        const val QUOTED_PRINTABLE = "quoted-printable"
    }
}

/**
 * The ordered parts of a message. Order is meaningful: SMIL is expected at
 * index 0 and the start part follows it.
 */
class PduBody {
    private val parts = mutableListOf<PduPart>()

    val size: Int get() = parts.size

    fun parts(): List<PduPart> = parts.toList()

    fun partAt(index: Int): PduPart? = parts.getOrNull(index)

    fun add(part: PduPart): PduPart {
        parts.add(part)
        return part
    }

    fun addAt(index: Int, part: PduPart) {
        parts.add(index.coerceIn(0, parts.size), part)
    }

    fun removeAll() = parts.clear()

    fun firstPartOfType(contentType: String): PduPart? =
        parts.firstOrNull { it.contentType?.let { t -> ContentTypes.normalize(t) == ContentTypes.normalize(contentType) } == true }

    val smil: PduPart? get() = parts.firstOrNull { it.isSmil }

    val text: PduPart? get() = firstPartOfType("text/plain")

    val attachments: List<PduPart> get() = parts.filterNot { it.isSmil || it.isTextual }

    /** Decoded text of the text/plain part, or null when there is none. */
    fun textContent(): String? {
        val part = text ?: return null
        val bytes = part.data ?: return null
        val charset = part.charset ?: CharacterSets.UTF_8
        return String(bytes, CharacterSets.charsetFor(charset))
    }

    fun totalDataBytes(): Long = parts.sumOf { (it.data?.size ?: 0).toLong() }
}

/**
 * A decoded or in-construction MMS PDU.
 *
 * Deliberately one class for all types: the header block already identifies the
 * type, and a sealed hierarchy would mean the persister and both codecs branch on
 * it anyway. [isSendReq] and friends exist so call sites can be explicit about
 * which type they require.
 */
open class Pdu(
    var messageType: Int = MessageType.SEND_REQ,
    var mmsVersion: Int = HeaderField.CURRENT_MMS_VERSION,
) {
    val headers = PduHeaders()
    var body: PduBody? = null

    var from: EncodedStringValue?
        get() = headers.fromOrNull()
        set(value) = headers.setFrom(value)

    val transactionId: String? get() = headers.textOrNull(HeaderField.TRANSACTION_ID)
    val contentType: String? get() = headers.contentTypeOrNull()
    val contentLocation: String? get() = headers.textOrNull(HeaderField.CONTENT_LOCATION)
    val subject: EncodedStringValue? get() = headers.encodedOrNull(HeaderField.SUBJECT)
    val to: List<EncodedStringValue> get() = headers.encodedList(HeaderField.TO)
    val cc: List<EncodedStringValue> get() = headers.encodedList(HeaderField.CC)
    val bcc: List<EncodedStringValue> get() = headers.encodedList(HeaderField.BCC)

    /** Seconds since the epoch, or null when the field is absent. */
    val dateSeconds: Long? get() = headers.longOrNull(HeaderField.DATE)

    val messageSize: Long? get() = headers.longOrNull(HeaderField.MESSAGE_SIZE)

    val messageClassOctet: Int?
        get() = headers.messageClassOctetOrDerived()

    val expirySeconds: Long? get() = headers.longOrNull(HeaderField.EXPIRY)

    val retrieveStatus: Int? get() = headers.octetOrNull(HeaderField.RETRIEVE_STATUS)
    val responseStatus: Int? get() = headers.octetOrNull(HeaderField.RESPONSE_STATUS)
    val status: Int? get() = headers.octetOrNull(HeaderField.STATUS)

    val isSendReq: Boolean get() = messageType == MessageType.SEND_REQ
    val isSendConf: Boolean get() = messageType == MessageType.SEND_CONF
    val isNotificationInd: Boolean get() = messageType == MessageType.NOTIFICATION_IND
    val isNotifyRespInd: Boolean get() = messageType == MessageType.NOTIFYRESP_IND
    val isRetrieveConf: Boolean get() = messageType == MessageType.RETRIEVE_CONF
    val isAcknowledgeInd: Boolean get() = messageType == MessageType.ACKNOWLEDGE_IND
    val isDeliveryInd: Boolean get() = messageType == MessageType.DELIVERY_IND
    val isReadRecInd: Boolean get() = messageType == MessageType.READ_REC_IND
    val isReadOrigInd: Boolean get() = messageType == MessageType.READ_ORIG_IND

    /**
     * True when the PDU declares a body it actually carries. A Retrieve-conf
     * with a non-OK retrieve status is headers only, and treating it as a
     * message would persist an empty body as if it were content.
     */
    val carriesContent: Boolean
        get() = when (messageType) {
            MessageType.SEND_REQ -> body != null
            MessageType.RETRIEVE_CONF ->
                body != null && (retrieveStatus == null || retrieveStatus == HeaderField.RETRIEVE_STATUS_OK)
            else -> false
        }

    /**
     * A transaction id is required on everything this app sends or answers. It
     * is generated from the clock, which is enough to correlate a transaction
     * within one device session.
     */
    fun ensureTransactionId(nowMillis: Long = System.currentTimeMillis()) {
        if (headers.textOrNull(HeaderField.TRANSACTION_ID).isNullOrBlank()) {
            headers.setText(HeaderField.TRANSACTION_ID, "T" + nowMillis.toString(16))
        }
    }

    fun copy(): Pdu = Pdu(messageType, mmsVersion).also {
        it.headers.copyFrom(headers)
        it.body = body?.let { body ->
            PduBody().also { copy -> body.parts().forEach { copy.add(it.copy()) } }
        }
    }

    override fun toString(): String = MessageType.name(messageType)
}
