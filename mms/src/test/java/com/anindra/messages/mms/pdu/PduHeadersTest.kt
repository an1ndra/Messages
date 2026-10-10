package com.anindra.messages.mms.pdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mandatory-header gate is what stops a truncated or hostile PDU reaching
 * the persister, so each type's requirement is asserted individually.
 */
class PduHeadersTest {

    private fun headersWithVersion() = PduHeaders().apply {
        setOctet(HeaderField.MMS_VERSION, HeaderField.MMS_VERSION_1_2)
    }

    private fun assertRejected(messageType: Int, block: PduHeaders.() -> Unit) {
        val headers = headersWithVersion().apply(block)
        try {
            headers.checkMandatory(messageType)
            throw AssertionError("expected rejection for type 0x%02X".format(messageType))
        } catch (expected: MalformedPduException) {
            // Expected.
        }
    }

    @Test
    fun everyTypeRequiresTheMmsVersion() {
        val headers = PduHeaders()
        try {
            headers.checkMandatory(MessageType.SEND_REQ)
            throw AssertionError("expected rejection")
        } catch (expected: MalformedPduException) {
            // MMS-Version gates everything else, so it is checked first.
        }
    }

    @Test
    fun aFullyPopulatedSendReqPasses() {
        val headers = headersWithVersion().apply {
            setContentType(ContentTypes.MULTIPART_RELATED)
            setFrom(EncodedStringValue.insertAddressToken())
            setText(HeaderField.TRANSACTION_ID, "T1")
        }
        headers.checkMandatory(MessageType.SEND_REQ)
    }

    @Test
    fun sendReqWithoutATransactionIdIsRejected() {
        assertRejected(MessageType.SEND_REQ) {
            setContentType(ContentTypes.MULTIPART_RELATED)
            setFrom(EncodedStringValue.insertAddressToken())
        }
    }

    @Test
    fun aNotificationNeedsItsLocationExpiryClassSizeAndTransaction() {
        assertRejected(MessageType.NOTIFICATION_IND) {
            setText(HeaderField.CONTENT_LOCATION, "http://mmsc/1")
        }

        val complete = headersWithVersion().apply {
            setText(HeaderField.CONTENT_LOCATION, "http://mmsc/1")
            setLong(HeaderField.EXPIRY, 1_700_000_000L)
            setMessageClassOctet(HeaderField.MESSAGE_CLASS_PERSONAL)
            setLong(HeaderField.MESSAGE_SIZE, 2048L)
            setText(HeaderField.TRANSACTION_ID, "T2")
        }
        complete.checkMandatory(MessageType.NOTIFICATION_IND)
    }

    @Test
    fun aRetrieveConfNeedsContentTypeAndDate() {
        assertRejected(MessageType.RETRIEVE_CONF) { setContentType(ContentTypes.MULTIPART_RELATED) }

        val complete = headersWithVersion().apply {
            setContentType(ContentTypes.MULTIPART_RELATED)
            setLong(HeaderField.DATE, 1_700_000_000L)
        }
        complete.checkMandatory(MessageType.RETRIEVE_CONF)
    }

    @Test
    fun aResponseTypeNeedsItsStatus() {
        assertRejected(MessageType.SEND_CONF) { setText(HeaderField.TRANSACTION_ID, "T3") }
        assertRejected(MessageType.NOTIFYRESP_IND) { setText(HeaderField.TRANSACTION_ID, "T4") }
    }

    @Test
    fun repeatingAddressFieldsAccumulateInsteadOfOverwriting() {
        val headers = PduHeaders()
        headers.addEncoded(HeaderField.TO, EncodedStringValue.utf8("+10000000001"))
        headers.addEncoded(HeaderField.TO, EncodedStringValue.utf8("+10000000002"))
        assertEquals(2, headers.encodedList(HeaderField.TO).size)
        assertEquals("+10000000002", headers.encodedList(HeaderField.TO)[1].text)
    }

    @Test
    fun settingAnEmptyAddressListClearsIt() {
        val headers = PduHeaders().apply { addEncoded(HeaderField.CC, EncodedStringValue.utf8("+1")) }
        headers.setEncodedList(HeaderField.CC, emptyList())
        assertFalse(headers.has(HeaderField.CC))
    }

    @Test
    fun messageClassIsEitherAnOctetOrATokenString() {
        val octet = PduHeaders().apply { setMessageClassOctet(HeaderField.MESSAGE_CLASS_PERSONAL) }
        assertEquals(HeaderField.MESSAGE_CLASS_PERSONAL, octet.messageClassOctetOrDerived())
        assertNull(octet.messageClassTextOrNull())

        val token = PduHeaders().apply { setMessageClassText(HeaderField.MESSAGE_CLASS_AUTO_STR) }
        assertEquals(HeaderField.MESSAGE_CLASS_AUTO, token.messageClassOctetOrDerived())
        assertEquals(HeaderField.MESSAGE_CLASS_PERSONAL_STR, "personal")
    }

    @Test
    fun anUnknownTokenTextHasNoOctetRatherThanAGuess() {
        val headers = PduHeaders().apply { setMessageClassText("carrier-invented") }
        assertNull(headers.messageClassOctetOrDerived())
    }

    @Test
    fun aRelativeExpiryIsHeldAsADeltaAndNotAsAnAbsoluteTime() {
        val headers = PduHeaders().apply { setLong(HeaderField.EXPIRY, HeaderField.VALUE_RELATIVE_TOKEN.toLong()) }
        assertNull(headers.longOrNull(HeaderField.EXPIRY))
        assertNull(headers.relativeExpirySeconds())

        val real = PduHeaders().apply { setLong(HeaderField.EXPIRY, 1_700_000_000L) }
        assertEquals(1_700_000_000L, real.longOrNull(HeaderField.EXPIRY))
    }

    @Test
    fun copyIsDeepForTheHeaderStore() {
        val original = headersWithVersion().apply {
            setText(HeaderField.TRANSACTION_ID, "T5")
            addEncoded(HeaderField.TO, EncodedStringValue.utf8("+1"))
        }
        val copy = PduHeaders().apply { copyFrom(original) }
        copy.setText(HeaderField.TRANSACTION_ID, "T6")
        copy.addEncoded(HeaderField.TO, EncodedStringValue.utf8("+2"))
        assertEquals("T5", original.textOrNull(HeaderField.TRANSACTION_ID))
        assertEquals(1, original.encodedList(HeaderField.TO).size)
        assertEquals(2, copy.encodedList(HeaderField.TO).size)
    }

    @Test
    fun fieldPresenceIsReportedForEveryKind() {
        val headers = PduHeaders().apply {
            setOctet(HeaderField.PRIORITY, HeaderField.PRIORITY_NORMAL)
            setLong(HeaderField.DATE, 1L)
            setText(HeaderField.MESSAGE_ID, "m1")
            setQuoted(HeaderField.CANCEL_ID, "c1")
            setEncoded(HeaderField.SUBJECT, EncodedStringValue.utf8("hi"))
            setContentType("text/plain")
            setFrom(EncodedStringValue.insertAddressToken())
            setMessageClassOctet(HeaderField.MESSAGE_CLASS_PERSONAL)
        }
        for (field in listOf(
            HeaderField.PRIORITY, HeaderField.DATE, HeaderField.MESSAGE_ID, HeaderField.CANCEL_ID,
            HeaderField.SUBJECT, HeaderField.CONTENT_TYPE, HeaderField.FROM, HeaderField.MESSAGE_CLASS,
        )) {
            assertTrue("field 0x%02X".format(field), headers.has(field))
        }
        assertFalse(headers.has(HeaderField.LIMIT))
    }
}
