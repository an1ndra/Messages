package com.anindra.messages.mms

import com.anindra.messages.mms.net.CarrierConfigSource
import com.anindra.messages.mms.net.CarrierProfile
import com.anindra.messages.mms.net.CarrierProfileStore
import com.anindra.messages.mms.net.MapCarrierValues
import com.anindra.messages.mms.pdu.ContentTypes
import com.anindra.messages.mms.pdu.EncodedStringValue
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduParser
import java.io.ByteArrayOutputStream

/**
 * Wire fixtures.
 *
 * `PduComposer` only composes the types this client sends, so an M-Send.conf and
 * an M-Retrieve.conf cannot be built with it -- they are written here octet by
 * octet instead, which also means the tests parse a carrier-shaped response
 * rather than one this package produced itself.
 */
object Wire {

    const val SUBSCRIPTION_ID = 1

    /** M-Send.conf, 1.2, with [responseStatus] and transaction id `T-1`. */
    fun sendConf(responseStatus: Int = HeaderField.RESPONSE_STATUS_OK): ByteArray = octets(
        MESSAGE_TYPE, MessageType.SEND_CONF,
        MMS_VERSION, MMS_VERSION_1_2,
        HeaderField.RESPONSE_STATUS, responseStatus,
        HeaderField.TRANSACTION_ID, 'T'.code, '-'.code, '1'.code, TERMINATOR,
    )

    /** M-Retrieve.conf, 1.2, with one `text/plain` part per entry. */
    fun retrieveConf(vararg parts: Pair<String, String>): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(parts.size)
        for ((name, text) in parts) {
            val data = text.toByteArray(Charsets.UTF_8)
            val value = octets(constrainedMedia(ContentTypes.normalize(TEXT_PLAIN)), PART_NAME) +
                ascii(name) + octets(TERMINATOR)
            val headers = octets(value.size) + value
            body.write(octets(headers.size, data.size))
            body.write(headers)
            body.write(data)
        }
        return octets(
            MESSAGE_TYPE, MessageType.RETRIEVE_CONF,
            MMS_VERSION, MMS_VERSION_1_2,
            CONTENT_TYPE, 0x01, constrainedMedia(ContentTypes.MULTIPART_RELATED),
            HeaderField.DATE, ZERO_LENGTH_INTEGER,
        ) + body.toByteArray()
    }

    /** M-Notification.ind, 1.2, carrying the headers the type makes mandatory. */
    fun notificationInd(
        transactionId: String = "T-notify",
        contentLocation: String = "http://mmsc.test/mms/inbox/7",
        expirySeconds: Long = 1_700_003_600L,
        messageSize: Long = 2048L,
    ): Pdu = Pdu(MessageType.NOTIFICATION_IND).apply {
        headers.setText(HeaderField.CONTENT_LOCATION, contentLocation)
        headers.setLong(HeaderField.EXPIRY, expirySeconds)
        headers.setMessageClassOctet(HeaderField.MESSAGE_CLASS_PERSONAL)
        headers.setLong(HeaderField.MESSAGE_SIZE, messageSize)
        headers.setText(HeaderField.TRANSACTION_ID, transactionId)
        from = EncodedStringValue.utf8("+15551230000")
    }

    private fun ascii(value: String) = value.toByteArray(Charsets.US_ASCII)

    private fun octets(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** A known media type goes out as a short-integer into the table, per §1. */
    private fun constrainedMedia(contentType: String): Int =
        SHORT_INTEGER_BASE or requireNotNull(ContentTypes.indexOf(contentType)) {
            "$contentType is not in the constrained-media table"
        }

    private const val MESSAGE_TYPE = 0x8C
    private const val MMS_VERSION = 0x8D
    private const val MMS_VERSION_1_2 = 0x92
    private const val CONTENT_TYPE = 0x84
    private const val PART_NAME = 0x85
    private const val TERMINATOR = 0x00
    private const val ZERO_LENGTH_INTEGER = 0x00
    private const val SHORT_INTEGER_BASE = 0x80
    private const val TEXT_PLAIN = "text/plain"
}

/** A [CarrierProfile] with only the keys a test names and no platform layer. */
fun profileOf(vararg values: Pair<String, Any>): CarrierProfile =
    CarrierProfile(MapCarrierValues(values.toMap()), null)

/**
 * A [CarrierProfileStore] resolving every subscription to [values], with no
 * platform layer on top.
 *
 * Built rather than subclassed: the store caches per subscription id, and
 * reaching into that cache from a test would couple the test to its shape.
 */
fun profileStoreOf(vararg values: Pair<String, Any>): CarrierProfileStore = CarrierProfileStore(
    defaults = MapCarrierValues(values.toMap()),
    source = CarrierConfigSource { null },
    defaultSubscriptionId = { Wire.SUBSCRIPTION_ID },
)

/** Parses [bytes], failing loudly rather than quietly returning null. */
fun parseOrFail(bytes: ByteArray): Pdu = PduParser(bytes).parse()
    ?: throw AssertionError("fixture did not parse: ${bytes.joinToString(" ") { "%02X".format(it) }}")