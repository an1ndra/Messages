package com.anindra.messages.mms.pdu

/**
 * The WSP header-field assignments for MMS, and the value type each carries.
 *
 * The field code doubles as the type tag on the wire: the first octet of a
 * header says which field it is, and that field's declared type says how the
 * value is encoded. Getting this table wrong is not a decode error — it produces
 * a PDU that parses and then fails at the carrier — so it is declared once here
 * and read by both the composer and the parser.
 */
object HeaderField {
    const val BCC = 0x81
    const val CC = 0x82
    const val CONTENT_LOCATION = 0x83
    const val CONTENT_TYPE = 0x84
    const val DATE = 0x85
    const val DELIVERY_REPORT = 0x86
    const val DELIVERY_TIME = 0x87
    const val EXPIRY = 0x88
    const val FROM = 0x89
    const val MESSAGE_CLASS = 0x8A
    const val MESSAGE_ID = 0x8B
    const val MESSAGE_TYPE = 0x8C
    const val MMS_VERSION = 0x8D
    const val MESSAGE_SIZE = 0x8E
    const val PRIORITY = 0x8F
    const val READ_REPORT = 0x90
    const val REPORT_ALLOWED = 0x91
    const val RESPONSE_STATUS = 0x92
    const val RESPONSE_TEXT = 0x93
    const val SENDER_VISIBILITY = 0x94
    const val STATUS = 0x95
    const val SUBJECT = 0x96
    const val TO = 0x97
    const val TRANSACTION_ID = 0x98
    const val RETRIEVE_STATUS = 0x99
    const val RETRIEVE_TEXT = 0x9A
    const val READ_STATUS = 0x9B
    const val REPLY_CHARGING = 0x9C
    const val REPLY_CHARGING_DEADLINE = 0x9D
    const val REPLY_CHARGING_ID = 0x9E
    const val REPLY_CHARGING_SIZE = 0x9F
    const val PREVIOUSLY_SENT_BY = 0xA0
    const val PREVIOUSLY_SENT_DATE = 0xA1
    const val STORE = 0xA2
    const val MM_STATE = 0xA3
    const val MM_FLAGS = 0xA4
    const val STORE_STATUS = 0xA5
    const val STORE_STATUS_TEXT = 0xA6
    const val STORED = 0xA7
    const val ATTRIBUTES = 0xA8
    const val TOTALS = 0xA9
    const val MBOX_TOTALS = 0xAA
    const val QUOTAS = 0xAB
    const val MBOX_QUOTAS = 0xAC
    const val MESSAGE_COUNT = 0xAD
    const val CONTENT = 0xAE
    const val START = 0xAF
    const val ADDITIONAL_HEADERS = 0xB0
    const val DISTRIBUTION_INDICATOR = 0xB1
    const val ELEMENT_DESCRIPTOR = 0xB2
    const val LIMIT = 0xB3
    const val RECOMMENDED_RETRIEVAL_MODE = 0xB4
    const val RECOMMENDED_RETRIEVAL_MODE_TEXT = 0xB5
    const val STATUS_TEXT = 0xB6
    const val APPLIC_ID = 0xB7
    const val REPLY_APPLIC_ID = 0xB8
    const val AUX_APPLIC_ID = 0xB9
    const val CONTENT_CLASS = 0xBA
    const val DRM_CONTENT = 0xBB
    const val ADAPTATION_ALLOWED = 0xBC
    const val REPLACE_ID = 0xBD
    const val CANCEL_ID = 0xBE
    const val CANCEL_STATUS = 0xBF

    const val FIRST = BCC
    const val LAST = CANCEL_STATUS

    /** How a field's value is encoded, which decides how many octets it takes. */
    enum class Kind {
        OCTET,
        LONG_INTEGER,
        INTEGER_VALUE,
        TEXT_STRING,
        QUOTED_STRING,
        ENCODED_STRING_VALUE,
        /** Repeats once per recipient rather than overwriting. */
        ENCODED_STRING_VALUE_LIST,
        CONTENT_TYPE,
        /** From is a token plus, optionally, an address. */
        FROM,
        /** An octet, or a token-text when the octet form does not apply. */
        MESSAGE_CLASS,
    }

    private val KINDS: Map<Int, Kind> = buildMap {
        fun octet(vararg fields: Int) = fields.forEach { put(it, Kind.OCTET) }
        fun long(vararg fields: Int) = fields.forEach { put(it, Kind.LONG_INTEGER) }
        fun integer(vararg fields: Int) = fields.forEach { put(it, Kind.INTEGER_VALUE) }
        fun text(vararg fields: Int) = fields.forEach { put(it, Kind.TEXT_STRING) }
        fun encoded(vararg fields: Int) = fields.forEach { put(it, Kind.ENCODED_STRING_VALUE) }
        fun encodedList(vararg fields: Int) = fields.forEach { put(it, Kind.ENCODED_STRING_VALUE_LIST) }

        encodedList(BCC, CC, TO)
        text(CONTENT_LOCATION, MESSAGE_ID, TRANSACTION_ID, RESPONSE_TEXT, REPLY_CHARGING_ID,
            REPLACE_ID, CANCEL_ID, APPLIC_ID, REPLY_APPLIC_ID, AUX_APPLIC_ID)
        put(CONTENT_TYPE, Kind.CONTENT_TYPE)
        long(DATE, DELIVERY_TIME, EXPIRY, MESSAGE_SIZE, PREVIOUSLY_SENT_DATE,
            REPLY_CHARGING_DEADLINE, REPLY_CHARGING_SIZE)
        octet(DELIVERY_REPORT, READ_REPORT, REPORT_ALLOWED, PRIORITY, SENDER_VISIBILITY,
            STATUS, STORE, STORED, TOTALS, QUOTAS, DISTRIBUTION_INDICATOR, DRM_CONTENT,
            ADAPTATION_ALLOWED, CANCEL_STATUS, RESPONSE_STATUS, RETRIEVE_STATUS, READ_STATUS,
            REPLY_CHARGING, MM_STATE, MESSAGE_TYPE, MMS_VERSION, CONTENT_CLASS, CONTENT,
            ATTRIBUTES, ELEMENT_DESCRIPTOR)
        encoded(FROM, SUBJECT, RETRIEVE_TEXT, STATUS_TEXT, STORE_STATUS_TEXT,
            PREVIOUSLY_SENT_BY, RECOMMENDED_RETRIEVAL_MODE_TEXT)
        put(FROM, Kind.FROM)
        put(MESSAGE_CLASS, Kind.MESSAGE_CLASS)
        put(STATUS_TEXT, Kind.ENCODED_STRING_VALUE)
        put(RECOMMENDED_RETRIEVAL_MODE, Kind.OCTET)
        integer(MESSAGE_COUNT, START, LIMIT)
        put(MBOX_TOTALS, Kind.OCTET)
        put(MBOX_QUOTAS, Kind.OCTET)
        put(ADDITIONAL_HEADERS, Kind.OCTET)
        put(MM_FLAGS, Kind.OCTET)
    }

    /** Null for a field code this version does not declare. */
    fun kindOf(field: Int): Kind? = KINDS[field]

    fun isKnown(field: Int): Boolean = KINDS.containsKey(field)

    // Value sets, used to validate what a carrier sends and to name what we get.

    const val VALUE_YES = 0x80
    const val VALUE_NO = 0x81

    const val VALUE_ABSOLUTE_TOKEN = 0x80
    const val VALUE_RELATIVE_TOKEN = 0x81

    const val MMS_VERSION_1_0 = (1 shl 4) or 0
    const val MMS_VERSION_1_1 = (1 shl 4) or 1
    const val MMS_VERSION_1_2 = (1 shl 4) or 2
    const val MMS_VERSION_1_3 = (1 shl 4) or 3

    /** 1.2, not 1.3: carriers and handsets in the field agree on 1.2 interoperating. */
    const val CURRENT_MMS_VERSION = MMS_VERSION_1_2

    const val STATUS_EXPIRED = 0x80
    const val STATUS_RETRIEVED = 0x81
    const val STATUS_REJECTED = 0x82
    const val STATUS_DEFERRED = 0x83
    const val STATUS_UNRECOGNIZED = 0x84
    const val STATUS_INDETERMINATE = 0x85
    const val STATUS_FORWARDED = 0x86
    const val STATUS_UNREACHABLE = 0x87

    const val MESSAGE_CLASS_PERSONAL = 0x80
    const val MESSAGE_CLASS_ADVERTISEMENT = 0x81
    const val MESSAGE_CLASS_INFORMATIONAL = 0x82
    const val MESSAGE_CLASS_AUTO = 0x83

    const val MESSAGE_CLASS_PERSONAL_STR = "personal"
    const val MESSAGE_CLASS_ADVERTISEMENT_STR = "advertisement"
    const val MESSAGE_CLASS_INFORMATIONAL_STR = "informational"
    const val MESSAGE_CLASS_AUTO_STR = "auto"

    const val PRIORITY_LOW = 0x80
    const val PRIORITY_NORMAL = 0x81
    const val PRIORITY_HIGH = 0x82

    const val SENDER_VISIBILITY_HIDE = 0x80
    const val SENDER_VISIBILITY_SHOW = 0x81

    const val READ_STATUS_READ = 0x80
    const val READ_STATUS_DELETED_WITHOUT_BEING_READ = 0x81

    const val RESPONSE_STATUS_OK = 0x80
    const val RESPONSE_STATUS_ERROR_TRANSIENT_FAILURE = 0xC0
    const val RESPONSE_STATUS_ERROR_TRANSIENT_SENDING_ADDRESS_UNRESOLVED = 0xC1
    const val RESPONSE_STATUS_ERROR_TRANSIENT_MESSAGE_NOT_FOUND = 0xC2
    const val RESPONSE_STATUS_ERROR_TRANSIENT_NETWORK_PROBLEM = 0xC3
    const val RESPONSE_STATUS_ERROR_TRANSIENT_PARTIAL_SUCCESS = 0xC4
    const val RESPONSE_STATUS_ERROR_PERMANENT_FAILURE = 0xE0

    const val RETRIEVE_STATUS_OK = 0x80
    const val RETRIEVE_STATUS_ERROR_TRANSIENT_FAILURE = 0xC0
    const val RETRIEVE_STATUS_ERROR_TRANSIENT_MESSAGE_NOT_FOUND = 0xC1
    const val RETRIEVE_STATUS_ERROR_TRANSIENT_NETWORK_PROBLEM = 0xC2
    const val RETRIEVE_STATUS_ERROR_PERMANENT_FAILURE = 0xE0
    const val RETRIEVE_STATUS_ERROR_PERMANENT_MESSAGE_NOT_FOUND = 0xE2
    const val RETRIEVE_STATUS_ERROR_PERMANENT_CONTENT_UNSUPPORTED = 0xE3
    const val RETRIEVE_STATUS_ERROR_END = 0xFF

    private val MESSAGE_CLASS_NAMES = mapOf(
        MESSAGE_CLASS_PERSONAL to MESSAGE_CLASS_PERSONAL_STR,
        MESSAGE_CLASS_ADVERTISEMENT to MESSAGE_CLASS_ADVERTISEMENT_STR,
        MESSAGE_CLASS_INFORMATIONAL to MESSAGE_CLASS_INFORMATIONAL_STR,
        MESSAGE_CLASS_AUTO to MESSAGE_CLASS_AUTO_STR,
    )

    fun messageClassName(octet: Int): String? = MESSAGE_CLASS_NAMES[octet]

    private val STATUS_NAMES = mapOf(
        STATUS_EXPIRED to "expired",
        STATUS_RETRIEVED to "retrieved",
        STATUS_REJECTED to "rejected",
        STATUS_DEFERRED to "deferred",
        STATUS_UNRECOGNIZED to "unrecognized",
        STATUS_INDETERMINATE to "indeterminate",
        STATUS_FORWARDED to "forwarded",
        STATUS_UNREACHABLE to "unreachable",
    )

    fun statusName(octet: Int): String? = STATUS_NAMES[octet]

    /** Octet for a message-class token string, or null when the name is unknown. */
    fun messageClassOctetFor(name: String): Int? =
        MESSAGE_CLASS_NAMES.entries.firstOrNull { it.value == name }?.key
}
