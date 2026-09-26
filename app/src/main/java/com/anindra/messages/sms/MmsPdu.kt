package com.anindra.messages.sms

import java.io.ByteArrayOutputStream

/**
 * WSP/MMS wire encoder for an outgoing `m-Send.req` PDU.
 *
 * Replaces the vendored AOSP `PduComposer`. Deliberately free of Android
 * framework types so the encode path is exercisable from plain JUnit; the
 * octet-level output is identical to what AOSP produced.
 *
 * References: WAP-230-WSP (encoding rules) and OMA-TS-MMS-ENC (headers).
 */
internal object MmsPdu {

    // X-Mms header field codes.
    const val BCC = 0x81
    const val CC = 0x82
    const val CONTENT_LOCATION = 0x83
    const val CONTENT_TYPE = 0x84
    const val DATE = 0x85
    const val DELIVERY_REPORT = 0x86
    const val EXPIRY = 0x88
    const val FROM = 0x89
    const val MESSAGE_CLASS = 0x8A
    const val MESSAGE_TYPE = 0x8C
    const val MMS_VERSION = 0x8D
    const val MESSAGE_SIZE = 0x8E
    const val PRIORITY = 0x8F
    const val READ_REPORT = 0x90
    const val SUBJECT = 0x96
    const val TO = 0x97
    const val TRANSACTION_ID = 0x98

    const val MESSAGE_TYPE_SEND_REQ = 0x80

    const val VALUE_YES = 0x80
    const val VALUE_NO = 0x81
    const val VALUE_ABSOLUTE_TOKEN = 0x80
    const val VALUE_RELATIVE_TOKEN = 0x81

    const val MMS_VERSION_1_2 = 0x12

    const val FROM_ADDRESS_PRESENT_TOKEN = 0x80
    const val FROM_INSERT_ADDRESS_TOKEN = 0x81

    const val MESSAGE_CLASS_PERSONAL = 0x80
    const val PRIORITY_NORMAL = 0x81

    // Content-type parameter codes used on part headers.
    const val P_CHARSET = 0x81
    const val P_DEP_NAME = 0x85
    const val P_CT_MR_TYPE = 0x89
    const val P_DEP_START = 0x8A
    const val PART_CONTENT_ID = 0xC0
    const val PART_CONTENT_LOCATION = 0x8E

    const val CHARSET_US_ASCII = 0x03
    const val CHARSET_UTF_8 = 0x6A

    const val MULTIPART_RELATED = "application/vnd.wap.multipart.related"
    const val APP_SMIL = "application/smil"

    private const val LENGTH_QUOTE = 31
    private const val TEXT_MAX = 127
    private const val QUOTED_STRING_FLAG = 34
    private const val LONG_INTEGER_LENGTH_MAX = 8
    private const val PDU_COMPOSER_BLOCK_SIZE = 1024

    /** Appended to a recipient that parses as a phone number (PLMN token). */
    private const val PLMN_SUFFIX = "/TYPE=PLMN"
    private val PHONE_ADDRESS = Regex("^\\+?[0-9.\\-]+$")

    /**
     * Well-known content types, indexed by their assigned value. A type that is
     * absent is encoded as a text-string, which the spec permits for any type.
     */
    private val WELL_KNOWN: Map<String, Int> =
        ("*/*|text/*|text/html|text/plain|text/x-hdml|text/x-ttml|text/x-vCalendar|"
            + "text/x-vCard|text/vnd.wap.wml|text/vnd.wap.wmlscript|text/vnd.wap.wta-event|"
            + "multipart/*|multipart/mixed|multipart/form-data|multipart/byterantes|"
            + "multipart/alternative|application/*|application/java-vm|"
            + "application/x-www-form-urlencoded|text/x-hdmlc|text/vnd.wap.wmlc|"
            + "text/vnd.wap.wmlscriptc|text/vnd.wap.wta-eventc|application/vnd.wap.uaprof|"
            + "application/vnd.wap.wtls-ca-certificate|"
            + "application/vnd.wap.wtls-user-certificate|application/x-x509-ca-cert|"
            + "application/x-x509-user-cert|image/*|image/gif|image/jpeg|image/tiff|image/png|"
            + "image/vnd.wap.wbmp|application/vnd.wap.multipart.*|"
            + "application/vnd.wap.multipart.mixed|application/vnd.wap.multipart.form-data|"
            + "application/vnd.wap.multipart.byteranges|"
            + "application/vnd.wap.multipart.alternative|application/xml|text/xml|"
            + "application/vnd.wap.wbxml|application/x-x968-cross-cert|"
            + "application/x-x968-ca-cert|application/x-x968-user-cert|text/vnd.wap.si|"
            + "application/vnd.wap.sic|text/vnd.wap.sl|text/vnd.wap.slc|text/vnd.wap.co|"
            + "application/vnd.wap.coc|application/vnd.wap.multipart.related|"
            + "application/vnd.wap.sia|text/vnd.wap.connectivity-xml|"
            + "application/vnd.wap.connectivity-wbxml|application/pkcs7-mime|"
            + "application/vnd.wap.hashed-certificate|application/vnd.wap.signed-cert|"
            + "application/vnd.wap.cert-response|application/xhtml+xml|application/wml+xml|"
            + "text/css|application/vnd.wap.mms-message|"
            + "application/vnd.wap.rollover-certificate|application/vnd.wap.locc+wbxml|"
            + "application/vnd.wap.loc+xml|application/vnd.syncml.dm+wbxml|"
            + "application/vnd.syncml.dm+xml|application/vnd.syncml.notification|"
            + "application/vnd.wap.xhtml+xml|application/vnd.wv.csp.cir|"
            + "application/vnd.oma.dd+xml|application/vnd.oma.drm.message|"
            + "application/vnd.oma.drm.content|application/vnd.oma.drm.rights+xml|"
            + "application/vnd.oma.drm.rights+wbxml|application/vnd.wv.csp+xml|"
            + "application/vnd.wv.csp+wbxml|application/vnd.syncml.ds.notification|"
            + "audio/*|video/*|application/vnd.oma.dd2+xml|application/mikey"
            ).split('|').mapIndexed { index, type -> type to index }.toMap()

    /** One entry of the `multipart/related` body. */
    class Part(
        val contentType: String,
        val name: String,
        val contentId: String,
        val charset: Int?,
        val data: ByteArray
    )

    /**
     * Composes an `m_SendReq`.
     *
     * [from] is the device's own number. When it is known the header carries an
     * address-present-token; otherwise the MMSC is left to stamp the sender via
     * an insert-address-token, which is also what AOSP emitted whenever the SIM
     * reported no number (the common case, since reading it needs
     * READ_PHONE_NUMBERS).
     */
    fun sendReq(
        transactionId: String,
        dateSeconds: Long,
        to: String,
        parts: List<Part>,
        from: String? = null,
        subject: String? = null,
        expirySeconds: Long? = null,
        messageClass: Int = MESSAGE_CLASS_PERSONAL,
        priority: Int = PRIORITY_NORMAL,
        deliveryReport: Int = VALUE_NO,
        readReport: Int = VALUE_NO
    ): ByteArray {
        val out = Enc()
        out.octet(MESSAGE_TYPE)
        out.octet(MESSAGE_TYPE_SEND_REQ)

        out.octet(TRANSACTION_ID)
        out.textString(transactionId.toByteArray(Charsets.UTF_8))

        out.octet(MMS_VERSION)
        out.shortInteger(MMS_VERSION_1_2)

        out.octet(DATE)
        out.longInteger(dateSeconds)

        if (from.isNullOrBlank()) {
            // Value-length 1, insert-address-token.
            out.octet(FROM)
            out.octet(1)
            out.octet(FROM_INSERT_ADDRESS_TOKEN)
        } else {
            val value = Enc()
            value.octet(FROM_ADDRESS_PRESENT_TOKEN)
            value.encodedString(from)
            out.octet(FROM)
            out.valueLength(value.size)
            out.raw(value.toBytes())
        }

        out.octet(TO)
        out.encodedString(to)

        if (subject != null) {
            out.octet(SUBJECT)
            out.encodedString(subject)
        }

        out.octet(MESSAGE_CLASS)
        out.octet(messageClass)

        if (expirySeconds != null) {
            out.octet(EXPIRY)
            val token = Enc()
            token.octet(VALUE_RELATIVE_TOKEN)
            token.longInteger(expirySeconds)
            out.valueLength(token.size)
            out.raw(token.toBytes())
        }

        out.octet(PRIORITY)
        out.octet(priority)

        out.octet(DELIVERY_REPORT)
        out.octet(deliveryReport)

        out.octet(READ_REPORT)
        out.octet(readReport)

        out.octet(CONTENT_TYPE)
        val contentType = Enc()
        appendMediaType(contentType, MULTIPART_RELATED)
        if (parts.isNotEmpty()) {
            contentType.octet(P_DEP_START)
            contentType.textString(angle(parts.first().contentId).toByteArray(Charsets.UTF_8))
            contentType.octet(P_CT_MR_TYPE)
            contentType.textString(parts.first().contentType.toByteArray(Charsets.UTF_8))
        }
        out.valueLength(contentType.size)
        out.raw(contentType.toBytes())

        out.uintvar(parts.size.toLong())
        for (part in parts) appendPart(out, part)
        return out.toBytes()
    }

    private fun appendPart(out: Enc, part: Part) {
        val header = Enc()
        val contentType = Enc()
        appendMediaType(contentType, part.contentType)
        contentType.octet(P_DEP_NAME)
        contentType.textString(part.name.toByteArray(Charsets.UTF_8))
        if (part.charset != null) {
            contentType.octet(P_CHARSET)
            contentType.shortInteger(part.charset)
        }
        header.valueLength(contentType.size)
        header.raw(contentType.toBytes())

        header.octet(PART_CONTENT_ID)
        header.quotedString(angle(part.contentId).toByteArray(Charsets.UTF_8))
        header.octet(PART_CONTENT_LOCATION)
        header.textString(part.name.toByteArray(Charsets.UTF_8))

        out.uintvar(header.size.toLong())
        out.uintvar(part.data.size.toLong())
        out.raw(header.toBytes())
        out.raw(part.data)
    }

    /** A well-known type goes out as a short-integer, anything else as text. */
    private fun appendMediaType(out: Enc, type: String) {
        val index = WELL_KNOWN[type]
        if (index != null) out.shortInteger(index) else out.textString(type.toByteArray(Charsets.UTF_8))
    }

    private fun angle(contentId: String): String =
        if (contentId.startsWith("<") && contentId.endsWith(">")) contentId else "<$contentId>"

    private fun Enc.encodedString(value: String) {
        val text = if (PHONE_ADDRESS.matches(value)) value + PLMN_SUFFIX else value
        val body = Enc()
        body.shortInteger(CHARSET_UTF_8)
        body.textString(text.toByteArray(Charsets.UTF_8))
        valueLength(body.size)
        raw(body.toBytes())
    }

    /** WSP octet writer. Sub-buffers are built separately and concatenated. */
    internal class Enc {
        private val buf = ByteArrayOutputStream(PDU_COMPOSER_BLOCK_SIZE)
        val size: Int get() = buf.size()

        fun octet(value: Int) = buf.write(value and 0xFF)
        fun raw(bytes: ByteArray) = buf.write(bytes, 0, bytes.size)

        /**
         * 1xxx xxxx holding a 0-127 value. Values above 127 are rejected rather
         * than truncated: a wrapped byte still parses, so a charset such as
         * UCS-2 (MIBEnum 1000) would silently decode as a different charset.
         */
        fun shortInteger(value: Int) {
            require(value in 0..127) { "short-integer out of range: $value" }
            octet(value or 0x80)
        }

        fun uintvar(value: Long) {
            var shifted = value
            var groups = 0
            while (shifted > 0x7F) {
                groups++
                shifted = shifted ushr 7
            }
            for (i in groups downTo 1) {
                octet((((value ushr (i * 7)) and 0x7F) or 0x80).toInt())
            }
            octet((value and 0x7F).toInt())
        }

        /**
         * Short-length octet count, then the big-endian octets. A zero value
         * still emits one octet: `Multi-octet-integer = 1*30 OCTET`.
         */
        fun longInteger(value: Long) {
            var octets = 1
            var rest = value
            while (rest != 0L && octets < LONG_INTEGER_LENGTH_MAX) {
                rest = rest ushr 8
                if (rest != 0L) octets++
            }
            octet(octets)
            for (i in octets - 1 downTo 0) {
                octet(((value ushr (i * 8)) and 0xFF).toInt())
            }
        }

        fun valueLength(value: Int) {
            if (value < LENGTH_QUOTE) {
                octet(value)
            } else {
                octet(LENGTH_QUOTE)
                uintvar(value.toLong())
            }
        }

        /** A leading 0x7F is required when the first octet has its high bit set. */
        fun textString(bytes: ByteArray) {
            if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) > TEXT_MAX) octet(TEXT_MAX)
            raw(bytes)
            octet(0)
        }

        fun quotedString(bytes: ByteArray) {
            octet(QUOTED_STRING_FLAG)
            raw(bytes)
            octet(0)
        }

        fun toBytes(): ByteArray = buf.toByteArray()
    }
}
