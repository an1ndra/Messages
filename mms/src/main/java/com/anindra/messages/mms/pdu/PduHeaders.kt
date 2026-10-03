package com.anindra.messages.mms.pdu

/**
 * The header block of a PDU: a typed field store plus the spec's mandatory-field
 * rules.
 *
 * Values are held as their wire type rather than as one generic `Any`, because
 * the same field can arrive as an octet or as token text (message class) and
 * because expiry and delivery-time arrive either absolute or as a relative token
 * that has to be resolved against the clock.
 */
class PduHeaders {
    private val octets = mutableMapOf<Int, Int>()
    private val longs = mutableMapOf<Int, Long>()
    private val texts = mutableMapOf<Int, String>()
    private val quoted = mutableMapOf<Int, String>()
    private val encoded = mutableMapOf<Int, EncodedStringValue>()
    private val encodedLists = mutableMapOf<Int, MutableList<EncodedStringValue>>()
    private var contentType: String? = null
    private var from: EncodedStringValue? = null
    private var messageClassOctet: Int? = null
    private var messageClassText: String? = null
    private var relativeExpiryDelta: Long? = null
    private var relativeDeliveryTimeDelta: Long? = null

    val fieldCodes: Set<Int>
        get() = buildSet {
            addAll(octets.keys); addAll(longs.keys); addAll(texts.keys)
            addAll(quoted.keys); addAll(encoded.keys); addAll(encodedLists.keys)
            if (contentType != null) add(HeaderField.CONTENT_TYPE)
            if (from != null) add(HeaderField.FROM)
            if (messageClassOctet != null || messageClassText != null) add(HeaderField.MESSAGE_CLASS)
        }

    fun has(field: Int): Boolean = field in fieldCodes

    fun clear() {
        octets.clear(); longs.clear(); texts.clear(); quoted.clear()
        encoded.clear(); encodedLists.clear()
        contentType = null; from = null
        messageClassOctet = null; messageClassText = null
        relativeExpiryDelta = null; relativeDeliveryTimeDelta = null
    }

    // Octet fields.

    fun setOctet(field: Int, value: Int) {
        octets[field] = value and 0xFF
    }

    fun octetOrNull(field: Int): Int? = octets[field]

    fun octetOr(field: Int, fallback: Int): Int = octets[field] ?: fallback

    // Long-integer fields.

    fun setLong(field: Int, value: Long) {
        when (field) {
            HeaderField.EXPIRY ->
                if (value == HeaderField.VALUE_RELATIVE_TOKEN.toLong()) {
                    relativeExpiryDelta = null; longs.remove(field)
                } else longs[field] = value

            HeaderField.DELIVERY_TIME ->
                if (value == HeaderField.VALUE_RELATIVE_TOKEN.toLong()) {
                    relativeDeliveryTimeDelta = null; longs.remove(field)
                } else longs[field] = value

            else -> longs[field] = value
        }
    }

    fun longOrNull(field: Int): Long? = longs[field]

    fun longOr(field: Int, fallback: Long): Long = longs[field] ?: fallback

    fun relativeExpirySeconds(): Long? = relativeExpiryDelta

    fun relativeDeliveryTimeSeconds(): Long? = relativeDeliveryTimeDelta

    // Text-string and quoted-string fields.

    fun setText(field: Int, value: String) {
        texts[field] = value
    }

    fun textOrNull(field: Int): String? = texts[field]

    fun textOr(field: Int, fallback: String): String = texts[field] ?: fallback

    fun setQuoted(field: Int, value: String) {
        quoted[field] = value
    }

    fun quotedOrNull(field: Int): String? = quoted[field]

    // Encoded-string-value fields.

    fun setEncoded(field: Int, value: EncodedStringValue) {
        encoded[field] = value
    }

    fun encodedOrNull(field: Int): EncodedStringValue? = encoded[field]

    /** Repeating fields accumulate; a second To does not replace the first. */
    fun addEncoded(field: Int, value: EncodedStringValue) {
        encodedLists.getOrPut(field) { mutableListOf() }.add(value)
    }

    fun encodedList(field: Int): List<EncodedStringValue> = encodedLists[field].orEmpty()

    fun setEncodedList(field: Int, values: List<EncodedStringValue>) {
        if (values.isEmpty()) encodedLists.remove(field) else encodedLists[field] = values.toMutableList()
    }

    // Fields with a bespoke shape.

    fun setContentType(value: String) {
        contentType = value
    }

    fun contentTypeOrNull(): String? = contentType

    fun contentTypeOr(fallback: String): String = contentType ?: fallback

    fun setFrom(value: EncodedStringValue?) {
        from = value
    }

    fun fromOrNull(): EncodedStringValue? = from

    fun setMessageClassOctet(value: Int) {
        messageClassOctet = value and 0xFF
        messageClassText = null
    }

    fun setMessageClassText(value: String) {
        messageClassText = value
        messageClassOctet = null
    }

    fun messageClassOctetOrNull(): Int? = messageClassOctet

    fun messageClassTextOrNull(): String? = messageClassText

    /** The octet when it is set, else the spec octet for a token-text class. */
    fun messageClassOctetOrDerived(): Int? =
        messageClassOctet ?: messageClassText?.let { HeaderField.messageClassOctetFor(it) }

    fun copyFrom(other: PduHeaders) {
        octets.putAll(other.octets)
        longs.putAll(other.longs)
        texts.putAll(other.texts)
        quoted.putAll(other.quoted)
        encoded.putAll(other.encoded)
        other.encodedLists.forEach { (field, values) ->
            encodedLists[field] = values.toMutableList()
        }
        other.contentType?.let { contentType = it }
        other.from?.let { from = it }
        messageClassOctet = other.messageClassOctet
        messageClassText = other.messageClassText
        relativeExpiryDelta = other.relativeExpiryDelta
        relativeDeliveryTimeDelta = other.relativeDeliveryTimeDelta
    }

    /**
     * Fields the spec requires for [messageType]. Enforced on parse so a PDU
     * missing something the carrier needs is rejected here rather than turning
     * into a confusing failure inside the transaction layer.
     */
    fun checkMandatory(messageType: Int) {
        // MMS-Version is a short-integer on the wire, so it lives in the octet
        // store, not the long store. Checking the wrong one silently rejected
        // every PDU.
        if (octetOrNull(HeaderField.MMS_VERSION) == null) {
            throw MalformedPduException("MMS-Version is missing")
        }
        val missing = when (messageType) {
            MessageType.SEND_REQ -> listOf(HeaderField.CONTENT_TYPE, HeaderField.FROM, HeaderField.TRANSACTION_ID)
            MessageType.SEND_CONF -> listOf(HeaderField.RESPONSE_STATUS, HeaderField.TRANSACTION_ID)
            MessageType.NOTIFICATION_IND -> listOf(
                HeaderField.CONTENT_LOCATION, HeaderField.EXPIRY,
                HeaderField.MESSAGE_CLASS, HeaderField.MESSAGE_SIZE, HeaderField.TRANSACTION_ID,
            )
            MessageType.NOTIFYRESP_IND -> listOf(HeaderField.STATUS, HeaderField.TRANSACTION_ID)
            MessageType.RETRIEVE_CONF -> listOf(HeaderField.CONTENT_TYPE, HeaderField.DATE)
            MessageType.DELIVERY_IND -> listOf(
                HeaderField.DATE, HeaderField.MESSAGE_ID, HeaderField.STATUS, HeaderField.TO,
            )
            MessageType.ACKNOWLEDGE_IND -> listOf(HeaderField.TRANSACTION_ID)
            MessageType.READ_ORIG_IND -> listOf(
                HeaderField.DATE, HeaderField.FROM, HeaderField.MESSAGE_ID,
                HeaderField.READ_STATUS, HeaderField.TO,
            )
            MessageType.READ_REC_IND -> listOf(
                HeaderField.FROM, HeaderField.MESSAGE_ID, HeaderField.READ_STATUS, HeaderField.TO,
            )
            else -> emptyList()
        }
        val absent = missing.filterNot { has(it) }
        if (absent.isNotEmpty()) {
            val names = absent.joinToString(",") { "0x%02X".format(it) }
            throw MalformedPduException("type 0x%02X missing headers %s".format(messageType, names))
        }
    }

    companion object {
        private val MESSAGE_CLASS_NAMES = mapOf(
            HeaderField.MESSAGE_CLASS_PERSONAL_STR to HeaderField.MESSAGE_CLASS_PERSONAL,
            HeaderField.MESSAGE_CLASS_ADVERTISEMENT_STR to HeaderField.MESSAGE_CLASS_ADVERTISEMENT,
            HeaderField.MESSAGE_CLASS_INFORMATIONAL_STR to HeaderField.MESSAGE_CLASS_INFORMATIONAL,
            HeaderField.MESSAGE_CLASS_AUTO_STR to HeaderField.MESSAGE_CLASS_AUTO,
        )
    }
}
