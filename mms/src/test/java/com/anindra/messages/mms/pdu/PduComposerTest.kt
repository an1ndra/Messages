package com.anindra.messages.mms.pdu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composer has no second opinion available: the parser will happily accept a
 * PDU whose octets the composer got wrong, so these tests pin the octets
 * themselves instead of only round-tripping them.
 */
class PduComposerTest {

    private fun hex(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun utf8Bytes(text: String) = text.toByteArray(Charsets.UTF_8)

    private fun roundTrip(pdu: Pdu, nowSeconds: Long = 1_700_000_000L): Pdu? {
        val bytes = PduComposer.compose(pdu) ?: return null
        return PduParser(bytes, nowSeconds = { nowSeconds }).parse()
    }

    private fun acknowledge(): Pdu = Pdu(MessageType.ACKNOWLEDGE_IND).apply {
        headers.setText(HeaderField.TRANSACTION_ID, "T-1")
    }

    private fun notifyResp(): Pdu = Pdu(MessageType.NOTIFYRESP_IND).apply {
        headers.setOctet(HeaderField.STATUS, HeaderField.STATUS_RETRIEVED)
        headers.setText(HeaderField.TRANSACTION_ID, "T-2")
    }

    private fun readRec(): Pdu = Pdu(MessageType.READ_REC_IND).apply {
        from = EncodedStringValue.utf8("+15559998888")
        headers.setText(HeaderField.MESSAGE_ID, "mid")
        headers.setOctet(HeaderField.READ_STATUS, HeaderField.READ_STATUS_READ)
        headers.setEncodedList(HeaderField.TO, listOf(EncodedStringValue.utf8("+15551230000")))
    }

    private fun textPart(name: String, text: String): PduPart = PduPart().apply {
        contentType = "text/plain"
        this.name = name
        charset = CharacterSets.UTF_8
        contentId = "<$name>"
        data = utf8Bytes(text)
    }

    private fun sendReq(vararg parts: PduPart, contentType: String = ContentTypes.MULTIPART_RELATED): Pdu =
        Pdu(MessageType.SEND_REQ).apply {
            from = EncodedStringValue.utf8("+15551230000")
            headers.setText(HeaderField.TRANSACTION_ID, "T-3")
            headers.setContentType(contentType)
            headers.setEncodedList(HeaderField.TO, listOf(EncodedStringValue.utf8("+15559998888")))
            body = PduBody().also { body -> parts.forEach { body.add(it) } }
        }

    @Test
    fun aNotifyRespIsExactlyElevenOctets() {
        // 0x8C type, 0x8D version 1.2 as a short-integer, 0x95 status, 0x98 "T-2".
        assertArrayEquals(hexOf("8C 83 8D 92 95 81 98 54 2D 32 00"), PduComposer.compose(notifyResp()))
    }

    @Test
    fun aReadRecEmitsEveryFieldInAscendingCodeOrder() {
        assertArrayEquals(
            hexOf(
                "89 10 80 0E EA 2B 31 35 35 35 39 39 39 38 38 38 38 00" +
                    "8B 6D 69 64 00" +
                    "8C 87" +
                    "8D 92" +
                    "97 0E EA 2B 31 35 35 35 31 32 33 30 30 30 30 00" +
                    "9B 80",
            ),
            PduComposer.compose(readRec()),
        )
    }

    @Test
    fun aSendReqWithSmilImageAndCaptionSurvivesEveryHeaderAndPart() {
        val smil = """
            <smil><head><layout><text height="320" width="240" type="textLayout"/></layout></head>
            <body><par dur="5000ms"><text src="hello.txt" region="Text"/></par>
            <par dur="5000ms"><img src="pic.jpg" region="Image"/></par></body></smil>
        """.trimIndent()
        val image = hex(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0xFF, 0xD9)
        val pdu = Pdu(MessageType.SEND_REQ).apply {
            from = EncodedStringValue.utf8("+15551230000")
            headers.setEncodedList(
                HeaderField.TO,
                listOf(EncodedStringValue.utf8("+15559998888"), EncodedStringValue.utf8("+15551112222")),
            )
            headers.setText(HeaderField.TRANSACTION_ID, "T-round-trip")
            headers.setEncoded(HeaderField.SUBJECT, EncodedStringValue.utf8("Grüße"))
            headers.setLong(HeaderField.DATE, 1_700_000_000L)
            headers.setLong(HeaderField.EXPIRY, 3600)
            headers.setOctet(HeaderField.PRIORITY, HeaderField.PRIORITY_HIGH)
            headers.setMessageClassOctet(HeaderField.MESSAGE_CLASS_PERSONAL)
            headers.setContentType(ContentTypes.MULTIPART_RELATED)
            body = PduBody().also { body ->
                body.add(PduPart().apply {
                    contentType = PduPart.APP_SMIL
                    name = "smil.xml"
                    contentId = "<smil>"
                    data = utf8Bytes(smil)
                })
                body.add(PduPart().apply {
                    contentType = "image/jpeg"
                    name = "pic.jpg"
                    contentId = "<pic.jpg>"
                    contentLocation = "pic.jpg"
                    data = image
                })
                body.add(PduPart().apply {
                    contentType = "text/plain"
                    name = "hello.txt"
                    contentId = "<hello.txt>"
                    charset = CharacterSets.UTF_8
                    data = utf8Bytes("hello")
                })
            }
        }

        val parsed = roundTrip(pdu, nowSeconds = 1_700_000_000L)
        assertNotNull(parsed)
        val out = parsed!!

        assertEquals(MessageType.SEND_REQ, out.messageType)
        assertEquals(HeaderField.MMS_VERSION_1_2, out.mmsVersion)
        assertEquals("+15551230000", out.from!!.text)
        assertEquals(listOf("+15559998888", "+15551112222"), out.to.map { it.text })
        assertEquals("T-round-trip", out.transactionId)
        assertEquals("Grüße", out.subject!!.text)
        assertEquals(1_700_000_000L, out.dateSeconds)
        assertEquals(1_700_000_000L + 3600, out.expirySeconds)
        assertEquals(HeaderField.PRIORITY_HIGH, out.headers.octetOrNull(HeaderField.PRIORITY))
        assertEquals(HeaderField.MESSAGE_CLASS_PERSONAL, out.messageClassOctet)
        assertEquals(ContentTypes.MULTIPART_RELATED, out.contentType)

        val body = out.body!!
        assertEquals(3, body.size)

        val smilPart = body.partAt(0)!!
        assertEquals(PduPart.APP_SMIL, smilPart.contentType)
        assertEquals("smil.xml", smilPart.name)
        assertEquals("<smil>", smilPart.contentId)
        assertArrayEquals(utf8Bytes(smil), smilPart.data)

        val imagePart = body.partAt(1)!!
        assertEquals("image/jpeg", imagePart.contentType)
        assertEquals("pic.jpg", imagePart.name)
        assertEquals("<pic.jpg>", imagePart.contentId)
        assertEquals("pic.jpg", imagePart.contentLocation)
        assertArrayEquals(image, imagePart.data)

        val textPart = body.partAt(2)!!
        assertEquals("text/plain", textPart.contentType)
        assertEquals("hello.txt", textPart.name)
        assertEquals(CharacterSets.UTF_8, textPart.charset)
        assertEquals("hello", body.textContent())
    }

    @Test
    fun anAcknowledgeRoundTrips() {
        val parsed = roundTrip(acknowledge())
        assertEquals(MessageType.ACKNOWLEDGE_IND, parsed!!.messageType)
        assertEquals("T-1", parsed.transactionId)
    }

    @Test
    fun aNotifyRespRoundTrips() {
        val parsed = roundTrip(notifyResp())
        assertEquals(MessageType.NOTIFYRESP_IND, parsed!!.messageType)
        assertEquals(HeaderField.STATUS_RETRIEVED, parsed.status)
        assertEquals("T-2", parsed.transactionId)
    }

    @Test
    fun aReadRecRoundTrips() {
        val parsed = roundTrip(readRec())
        assertEquals(MessageType.READ_REC_IND, parsed!!.messageType)
        assertEquals("+15559998888", parsed.from!!.text)
        assertEquals("mid", parsed.headers.textOrNull(HeaderField.MESSAGE_ID))
        assertEquals(HeaderField.READ_STATUS_READ, parsed.headers.octetOrNull(HeaderField.READ_STATUS))
        assertEquals(listOf("+15551230000"), parsed.to.map { it.text })
    }

    @Test
    fun twoRecipientsEmitTheFieldCodeTwiceAndBothParseBack() {
        val pdu = sendReq(textPart("a.txt", "a"))
        pdu.headers.setEncodedList(
            HeaderField.TO,
            listOf(EncodedStringValue.utf8("+15551110000"), EncodedStringValue.utf8("+15552220000")),
        )
        val bytes = PduComposer.compose(pdu)!!
        var occurrences = 0
        for (index in bytes.indices) {
            if (bytes[index].toInt() and 0xFF == HeaderField.TO) occurrences++
        }
        assertEquals(2, occurrences)
        assertEquals(
            listOf("+15551110000", "+15552220000"),
            PduParser(bytes).parse()!!.to.map { it.text },
        )
    }

    @Test
    fun aFromWithNoAddressIsTheFieldCodeAndOneTokenOctet() {
        val pdu = sendReq(textPart("a.txt", "a"))
        pdu.from = EncodedStringValue.insertAddressToken()
        val bytes = PduComposer.compose(pdu)!!
        assertTrue(containsOctets(bytes, hexOf("89 01 81")))
        assertEquals(
            EncodedStringValue.insertAddressToken(),
            PduParser(bytes).parse()!!.from,
        )
    }

    @Test
    fun expiryIsWrappedInAValueLengthWithARelativeToken() {
        val pdu = sendReq(textPart("a.txt", "a"))
        pdu.headers.setLong(HeaderField.EXPIRY, 3600)
        val bytes = PduComposer.compose(pdu)!!
        // <0x88> <value-length 4> <0x81 relative> <long-integer 0x02 0x0E 0x10>
        assertTrue(containsOctets(bytes, hexOf("88 04 81 02 0E 10")))
    }

    @Test
    fun deliveryTimeIsWrappedTheSameWayAsExpiry() {
        val pdu = sendReq(textPart("a.txt", "a"))
        pdu.headers.setLong(HeaderField.DELIVERY_TIME, 3600)
        val bytes = PduComposer.compose(pdu)!!
        assertTrue(containsOctets(bytes, hexOf("87 04 81 02 0E 10")))
    }

    @Test
    fun messageClassUsesTheOctetFormWhenOneIsSetAndTokenTextOtherwise() {
        // No content-id on the first part, so the container header carries no
        // start parameter and the only 0x8A left is the message class.
        val octet = sendReq(textPart("a.txt", "a").apply { contentId = null }).apply {
            headers.setMessageClassOctet(HeaderField.MESSAGE_CLASS_ADVERTISEMENT)
        }
        assertTrue(containsOctets(PduComposer.compose(octet)!!, hexOf("8A 81")))

        val token = sendReq(textPart("a.txt", "a").apply { contentId = null }).apply {
            headers.setMessageClassText(HeaderField.MESSAGE_CLASS_ADVERTISEMENT_STR)
        }
        assertTrue(
            containsOctets(
                PduComposer.compose(token)!!,
                hexOf("8A 61 64 76 65 72 74 69 73 65 6D 65 6E 74 00"),
            ),
        )
        assertEquals(
            HeaderField.MESSAGE_CLASS_ADVERTISEMENT,
            PduParser(PduComposer.compose(token)!!).parse()!!.messageClassOctet,
        )
    }

    @Test
    fun aOnePartSendReqIsPinnedOctetForOctet() {
        assertArrayEquals(
            hexOf(
                // 0x89 From with an address: 0x80 token, then the encoded value.
                "89 10 80 0E EA 2B 31 35 35 35 31 32 33 30 30 30 30 00" +
                    "8C 80" +
                    "8D 92" +
                    // 0x97 To: a phone recipient goes out as number/TYPE=PLMN,
                    // so the encoded value is 24 octets, not 14.
                    "97 18 EA 2B 31 35 35 35 39 39 39 38 38 38 38 2F 54 59 50 45 3D 50 4C 4D 4E 00" +
                    "98 54 2D 33 00" +
                    // 0x84 closes the header block: 0xB3 multipart/related,
                    // 0x8A start, 0x89 type — the last header before the body,
                    // where receivers stop reading headers.
                    "84 1A B3 8A 3C 68 65 6C 6C 6F 2E 74 78 74 3E 00 89 74 65 78 74 2F 70 6C 61 69 6E 00" +
                    // One entry: header-length 0x1D, data-length 2, headers, data.
                    "01 1D 02 0E 83 85 68 65 6C 6C 6F 2E 74 78 74 00 81 EA C0 22 3C 68 65 6C 6C 6F 2E 74 78 74 3E 00" +
                    "68 69",
            ),
            PduComposer.compose(sendReq(textPart("hello.txt", "hi"))),
        )
    }

    @Test
    fun theContainerContentTypeClosesTheHeaderBlock() {
        val bytes = PduComposer.compose(sendReq(textPart("hello.txt", "hi")))!!
        // Receivers stop reading headers at X-Mms-Content-Type, so it must be
        // the last header — after the transaction id, whatever its field code
        // (0x84) sorts as.
        val container = indexOfRun(bytes, hexOf("84 1A B3"))
        val transactionId = indexOfRun(bytes, hexOf("98 54 2D 33 00"))
        assertTrue("Content-Type must follow every other header", transactionId in 0 until container)
    }

    @Test
    fun aPartWithNoDataComposesWithAZeroDataLength() {
        val part = PduPart().apply {
            contentType = "text/plain"
            name = "empty.txt"
        }
        val bytes = PduComposer.compose(sendReq(part))!!
        val parsed = PduParser(bytes).parse()!!
        assertEquals(0, parsed.body!!.partAt(0)!!.data!!.size)
    }

    @Test
    fun aSendReqWithNoBodyIsNotComposable() {
        val pdu = sendReq().apply { body = null }
        assertNull(PduComposer.compose(pdu))
    }

    @Test
    fun aSendReqWithANonMultipartContentTypeIsNotComposable() {
        assertNull(PduComposer.compose(sendReq(textPart("a.txt", "a"), contentType = "text/plain")))
    }

    @Test
    fun aPartWithNoContentTypeIsNotComposable() {
        val part = PduPart().apply { name = "mystery.bin" }
        assertNull(PduComposer.compose(sendReq(part)))
    }

    @Test
    fun aPartWithNoNameFilenameOrContentLocationIsNotComposable() {
        val part = PduPart().apply {
            contentType = "image/jpeg"
            contentId = "<anonymous>"
        }
        assertNull(PduComposer.compose(sendReq(part)))
    }

    @Test
    fun aPartNamedOnlyByContentLocationIsComposable() {
        val part = PduPart().apply {
            contentType = "image/jpeg"
            contentLocation = "photo.jpg"
            data = hex(0x00)
        }
        val parsed = PduParser(PduComposer.compose(sendReq(part))!!).parse()!!
        assertEquals("photo.jpg", parsed.body!!.partAt(0)!!.name)
    }

    @Test
    fun aMessageTypeThisComposerDoesNotBuildIsNotComposable() {
        for (type in listOf(
            MessageType.SEND_CONF,
            MessageType.NOTIFICATION_IND,
            MessageType.RETRIEVE_CONF,
            MessageType.DELIVERY_IND,
            MessageType.READ_ORIG_IND,
        )) {
            val pdu = Pdu(type)
            pdu.from = EncodedStringValue.utf8("+15551230000")
            pdu.headers.setText(HeaderField.TRANSACTION_ID, "T")
            pdu.headers.setText(HeaderField.MESSAGE_ID, "m")
            pdu.headers.setContentType(ContentTypes.MULTIPART_MIXED)
            assertNull("type 0x%02X should not compose".format(type), PduComposer.compose(pdu))
        }
    }

    @Test
    fun aMissingMandatoryHeaderIsNotComposable() {
        assertNull(PduComposer.compose(Pdu(MessageType.ACKNOWLEDGE_IND)))
        assertNull(PduComposer.compose(Pdu(MessageType.NOTIFYRESP_IND).apply {
            headers.setText(HeaderField.TRANSACTION_ID, "T")
        }))
        assertNull(PduComposer.compose(Pdu(MessageType.READ_REC_IND)))
    }

    @Test
    fun aSendReqWithNoRecipientIsNotComposable() {
        val pdu = sendReq(textPart("a.txt", "a")).apply {
            headers.setEncodedList(HeaderField.TO, emptyList())
        }
        assertNull(PduComposer.compose(pdu))
    }

    @Test
    fun aValueThatExceedsItsPrimitiveIsNotComposableRatherThanTruncated() {
        // MMS-Version is a short-integer, so 0xFF cannot be encoded in seven bits
        // and must not be written truncated to 0x7F.
        val versionTooWide = sendReq(textPart("a.txt", "a")).apply {
            headers.setOctet(HeaderField.MMS_VERSION, 0xFF)
        }
        assertNull(PduComposer.compose(versionTooWide))
    }

    @Test
    fun composingTheSamePduTwiceProducesTheSameOctets() {
        val pdu = sendReq(textPart("a.txt", "a"), textPart("b.txt", "b"))
        assertArrayEquals(PduComposer.compose(pdu), PduComposer.compose(pdu))
        assertTrue(PduComposer.compose(pdu)!!.size > 0)
    }

    private fun hexOf(literal: String): ByteArray {
        val digits = literal.filterNot { it.isWhitespace() }
        require(digits.length % 2 == 0) { "not a whole number of octets: $literal" }
        return ByteArray(digits.length / 2) {
            digits.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Whether an exact octet run appears. Matching the whole run rather than a
     * lone field code is what keeps the container header's 0x89 type parameter
     * from being mistaken for the From field.
     */
    private fun containsOctets(bytes: ByteArray, sequence: ByteArray): Boolean =
        indexOfRun(bytes, sequence) >= 0

    private fun indexOfRun(bytes: ByteArray, sequence: ByteArray): Int {
        for (start in 0..bytes.size - sequence.size) {
            if (sequence.contentEquals(bytes.copyOfRange(start, start + sequence.size))) return start
        }
        return -1
    }
}