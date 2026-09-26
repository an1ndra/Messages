package com.anindra.messages.sms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-format contract for the hand-rolled `m-Send.req` encoder.
 *
 * [GOLDEN_CAPTION] / [GOLDEN_NO_CAPTION] were produced on an emulator by running
 * this encoder and the vendored AOSP `PduComposer` in the same process with
 * identical inputs; both reported `equal=true`. They are embedded here rather
 * than read from the repo so the assertion data travels with the test and
 * survives a clean checkout, and so any change to them is a reviewable edit to
 * a byte-identity claim rather than a resource swap.
 */
class MmsPduTest {

    private val png = hex(PNG_HEX)

    private fun captionParts() = listOf(
        MmsPdu.Part("application/smil", "smil.xml", "smil", null, SMIL_WITH_CAPTION.toByteArray()),
        MmsPdu.Part("image/png", "image", "image", null, png),
        MmsPdu.Part("text/plain", "text", "text", MmsPdu.CHARSET_UTF_8, "hello".toByteArray())
    )

    private fun noCaptionParts() = listOf(
        MmsPdu.Part("application/smil", "smil.xml", "smil", null, SMIL_NO_CAPTION.toByteArray()),
        MmsPdu.Part("image/png", "image", "image", null, png)
    )

    private fun sendReq(
        parts: List<MmsPdu.Part>,
        from: String? = null,
        subject: String? = null,
        expirySeconds: Long? = EXPIRY_SECONDS
    ) = MmsPdu.sendReq(TRANSACTION_ID, DATE_SECONDS, TO, parts, from, subject, expirySeconds)

    // ---------------------------------------------------------------- golden

    @Test
    fun matchesAospGoldenBytesWithCaption() {
        val pdu = sendReq(captionParts())
        assertEquals(433, pdu.size)
        assertArrayEquals(hex(GOLDEN_CAPTION), pdu)
    }

    @Test
    fun matchesAospGoldenBytesWithoutCaption() {
        val pdu = sendReq(noCaptionParts())
        assertEquals(383, pdu.size)
        assertArrayEquals(hex(GOLDEN_NO_CAPTION), pdu)
    }

    @Test
    fun bothShapesShareTheSameHeaderBlock() {
        // The two goldens differ only past the part count: same fields, same
        // order, same value-lengths, different body.
        val withCaption = hex(GOLDEN_CAPTION)
        val withoutCaption = hex(GOLDEN_NO_CAPTION)
        assertArrayEquals(withCaption.copyOfRange(0, 94), withoutCaption.copyOfRange(0, 94))
        assertEquals(3, withCaption[94].toInt())
        assertEquals(2, withoutCaption[94].toInt())
    }

    // ------------------------------------------------------- header framing

    @Test
    fun beginsWithMessageTypeSendReq() {
        // MmsService.isRawPduSendReq hard-codes this 2-byte check, so these two
        // octets are not negotiable with the MMSC.
        val pdu = sendReq(captionParts())
        assertArrayEquals(byteArrayOf(0x8C.toByte(), 0x80.toByte()), pdu.copyOfRange(0, 2))
        assertEquals(MmsPdu.MESSAGE_TYPE, pdu[0].toInt() and 0xFF)
        assertEquals(MmsPdu.MESSAGE_TYPE_SEND_REQ, pdu[1].toInt() and 0xFF)
    }

    @Test
    fun encodesNulTerminatedTransactionId() {
        val pdu = sendReq(captionParts())
        assertEquals(MmsPdu.TRANSACTION_ID, pdu[2].toInt() and 0xFF)
        assertArrayEquals(
            TRANSACTION_ID.toByteArray() + byteArrayOf(0x00),
            pdu.copyOfRange(3, 13)
        )
        assertEquals(0, pdu[12].toInt())
    }

    @Test
    fun encodesMmsVersionAsShortInteger() {
        val pdu = sendReq(captionParts())
        assertEquals(MmsPdu.MMS_VERSION, pdu[13].toInt() and 0xFF)
        // 0x12 == MMS 1.2, carried in the 1xxx xxxx short-integer form.
        assertEquals(MmsPdu.MMS_VERSION_1_2 or 0x80, pdu[14].toInt() and 0xFF)
    }

    @Test
    fun encodesDateAsLongIntegerOfSeconds() {
        val pdu = sendReq(captionParts())
        assertEquals(MmsPdu.DATE, pdu[15].toInt() and 0xFF)
        assertArrayEquals(
            byteArrayOf(0x04, 0x65, 0x53, 0xF1.toByte(), 0x00),
            pdu.copyOfRange(16, 21)
        )
        assertEquals(DATE_SECONDS, 0x6553F100L)
    }

    @Test
    fun fromAbsentEmitsInsertAddressToken() {
        val from = sendReq(captionParts(), from = null).copyOfRange(21, 24)
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x01, 0x81.toByte()), from)
        assertEquals(MmsPdu.FROM, from[0].toInt() and 0xFF)
        assertEquals(MmsPdu.FROM_INSERT_ADDRESS_TOKEN, from[2].toInt() and 0xFF)
    }

    @Test
    fun fromPresentWrapsTheAddressInAnAddressPresentToken() {
        val pdu = sendReq(captionParts(), from = "+15550001111")
        // 89 | 1A value-length | 80 address-present | 18 | EA charset |
        // "+15550001111/TYPE=PLMN" | NUL
        assertArrayEquals(
            hex("891a8018ea") + "+15550001111/TYPE=PLMN".toByteArray() + byteArrayOf(0x00),
            pdu.copyOfRange(21, 49)
        )
        assertEquals(MmsPdu.FROM, pdu[21].toInt() and 0xFF)
        assertEquals(26, pdu[22].toInt() and 0xFF)
        assertEquals(MmsPdu.FROM_ADDRESS_PRESENT_TOKEN, pdu[23].toInt() and 0xFF)
    }

    @Test
    fun fromBlankIsTreatedAsAbsent() {
        assertArrayEquals(sendReq(captionParts(), from = null), sendReq(captionParts(), from = "  "))
    }

    @Test
    fun toCarriesThePlmnSuffixAndAUtf8Charset() {
        val pdu = sendReq(captionParts())
        assertEquals(MmsPdu.TO, pdu[24].toInt() and 0xFF)
        assertEquals(24, pdu[25].toInt() and 0xFF)
        // Charset 0x6A is MIBEnum 106 (UTF-8); the short-integer form sets the
        // high bit, so 0x6A goes out as 0xEA.
        assertEquals(MmsPdu.CHARSET_UTF_8 or 0x80, pdu[26].toInt() and 0xFF)
        assertArrayEquals(
            "$TO$PLMN".toByteArray() + byteArrayOf(0x00),
            pdu.copyOfRange(27, 50)
        )
        assertEquals(MmsPdu.MESSAGE_CLASS, pdu[50].toInt() and 0xFF)
    }

    @Test
    fun nonPhoneRecipientLosesThePlmnSuffix() {
        val pdu = MmsPdu.sendReq(TRANSACTION_ID, DATE_SECONDS, "user@example.test", captionParts())
        val text = ascii(pdu, 0, pdu.size)
        assertTrue(text.contains("user@example.test"))
        assertFalse(text.contains(PLMN))
    }

    // --------------------------------------------------------- content type

    @Test
    fun topLevelContentTypeCountsOnlyItsOwnValue() {
        val pdu = sendReq(captionParts())
        // 0xB3 is the short-integer 0x33, multipart.related. The value-length
        // spans the content-type-value only: the body header and the first
        // part's headers follow it, and counting them is the classic
        // hand-rolled bug because the part framing is self-relative.
        assertArrayEquals(
            byteArrayOf(0x84.toByte(), 0x1B, 0xB3.toByte(), 0x8A.toByte()) +
                "<smil>".toByteArray() + byteArrayOf(0x00) +
                byteArrayOf(0x89.toByte()) +
                MmsPdu.APP_SMIL.toByteArray() + byteArrayOf(0x00),
            pdu.copyOfRange(65, 94)
        )
        // 0xB3(1) + 0x8A(1) + "<smil>"+NUL(7) + 0x89(1) + "application/smil"+NUL(17)
        assertEquals(1 + 1 + 7 + 1 + 17, pdu[66].toInt() and 0xFF)
        // The byte right after the value is the part count, proving the
        // value-length stopped at the right place.
        assertEquals(3, pdu[94].toInt())
    }

    // ----------------------------------------------------------- field order

    @Test
    fun headerFieldOrderMatchesAospEmitOrder() {
        val pdu = sendReq(captionParts())
        val fields = listOf(
            Triple("message-type", 0, MmsPdu.MESSAGE_TYPE),
            Triple("transaction-id", 2, MmsPdu.TRANSACTION_ID),
            Triple("mms-version", 13, MmsPdu.MMS_VERSION),
            Triple("date", 15, MmsPdu.DATE),
            Triple("from", 21, MmsPdu.FROM),
            Triple("to", 24, MmsPdu.TO),
            Triple("message-class", 50, MmsPdu.MESSAGE_CLASS),
            Triple("expiry", 52, MmsPdu.EXPIRY),
            Triple("priority", 59, MmsPdu.PRIORITY),
            Triple("delivery-report", 61, MmsPdu.DELIVERY_REPORT),
            Triple("read-report", 63, MmsPdu.READ_REPORT),
            Triple("content-type", 65, MmsPdu.CONTENT_TYPE)
        )
        fields.forEach { (name, offset, code) ->
            assertEquals(name, code, pdu[offset].toInt() and 0xFF)
        }
        val offsetOf = { code: Int -> fields.first { it.third == code }.second }
        assertTrue(offsetOf(MmsPdu.MESSAGE_CLASS) < offsetOf(MmsPdu.EXPIRY))
        assertTrue(offsetOf(MmsPdu.EXPIRY) < offsetOf(MmsPdu.PRIORITY))
        assertEquals(MmsPdu.CONTENT_TYPE, fields.last().third)
    }

    @Test
    fun encodesMessageClassExpiryPriorityAndReportFlags() {
        val pdu = sendReq(captionParts())
        assertEquals(MmsPdu.MESSAGE_CLASS_PERSONAL, pdu[51].toInt() and 0xFF)
        // 88 | 05 value-length | 81 relative-token | 03 09 3A 80 == 604800 s
        assertArrayEquals(
            byteArrayOf(0x88.toByte(), 0x05, 0x81.toByte(), 0x03, 0x09, 0x3A, 0x80.toByte()),
            pdu.copyOfRange(52, 59)
        )
        assertEquals(EXPIRY_SECONDS, 0x093A80L)
        assertEquals(MmsPdu.PRIORITY_NORMAL, pdu[60].toInt() and 0xFF)
        assertEquals(MmsPdu.VALUE_NO, pdu[62].toInt() and 0xFF)
        assertEquals(MmsPdu.VALUE_NO, pdu[64].toInt() and 0xFF)
    }

    @Test
    fun subjectIsOmittedUnlessSupplied() {
        val withSubject = MmsPdu.sendReq(
            TRANSACTION_ID, DATE_SECONDS, TO, captionParts(), subject = "hi"
        )
        // 96 | 04 value-length | EA charset | "hi" | NUL
        assertArrayEquals(
            hex("9604ea") + "hi".toByteArray() + byteArrayOf(0x00),
            withSubject.copyOfRange(50, 56)
        )
        assertEquals(MmsPdu.MESSAGE_CLASS, withSubject[56].toInt() and 0xFF)
        assertEquals(MmsPdu.MESSAGE_CLASS, sendReq(captionParts())[50].toInt() and 0xFF)
    }

    @Test
    fun expiryIsOmittedWhenNull() {
        val pdu = sendReq(captionParts(), expirySeconds = null)
        val golden = hex(GOLDEN_CAPTION)
        // Everything before and after the 7-octet expiry field is untouched.
        assertArrayEquals(golden.copyOfRange(0, 52), pdu.copyOfRange(0, 52))
        assertArrayEquals(golden.copyOfRange(59, 95), pdu.copyOfRange(52, 88))
    }

    // --------------------------------------------------------- part framing

    @Test
    fun partsAreFramedByHeaderAndDataLengthPairs() {
        val pdu = sendReq(captionParts())
        // Pin the raw framing octets so a misread of the varint helper cannot
        // make the parsed view and the golden view agree on one wrong answer.
        assertArrayEquals(
            byteArrayOf(0x03, 0x2F, 0x81.toByte(), 0x1B),
            pdu.copyOfRange(94, 98)
        )
        assertArrayEquals(byteArrayOf(0x1A, 0x49), pdu.copyOfRange(300, 302))
        assertArrayEquals(byteArrayOf(0x19, 0x05), pdu.copyOfRange(401, 403))

        val frames = partFrames(pdu)
        assertEquals(3, frames.size)
        assertEquals(listOf(47L, 26L, 25L), frames.map { it.headerLength })
        assertEquals(listOf(155L, 73L, 5L), frames.map { it.dataLength })
        assertEquals(433, frames.last().offset + frames.last().length)
    }

    @Test
    fun smilPartIsFirstSoTheStartParameterResolves() {
        val pdu = sendReq(captionParts())
        val frames = partFrames(pdu)
        assertTrue(ascii(frames[0].header, 0, frames[0].header.size).contains(MmsPdu.APP_SMIL))
        // The top-level start= is "<smil>", which only part 0 can satisfy.
        assertArrayEquals(
            byteArrayOf(MmsPdu.P_DEP_START.toByte()) + "<smil>".toByteArray() + byteArrayOf(0),
            pdu.copyOfRange(68, 76)
        )
    }

    @Test
    fun partDataIsTheAttachmentAndCaptionVerbatim() {
        val pdu = sendReq(captionParts())
        assertEquals(155, SMIL_WITH_CAPTION.toByteArray().size)
        assertArrayEquals(SMIL_WITH_CAPTION.toByteArray(), pdu.copyOfRange(145, 300))
        assertEquals(73, png.size)
        assertArrayEquals(png, pdu.copyOfRange(328, 401))
        assertArrayEquals("hello".toByteArray(), pdu.copyOfRange(428, 433))
    }

    @Test
    fun noCaptionVariantOmitsTheTextPart() {
        val pdu = sendReq(noCaptionParts())
        val frames = partFrames(pdu)
        assertEquals(2, frames.size)
        assertEquals(listOf(47L, 26L), frames.map { it.headerLength })
        // The caption-free SMIL drops <text src="text"/>, 18 octets shorter.
        assertEquals(listOf(137L, 73L), frames.map { it.dataLength })
        assertFalse(ascii(pdu, 0, pdu.size).contains("text/plain"))
    }

    // --------------------------------------------------------- part headers

    @Test
    fun partHeaderUsesAQuotedStringContentIdAndATextStringLocation() {
        val pdu = sendReq(captionParts())
        // C0 22 "<image>" NUL | 8E "image" NUL
        assertArrayEquals(
            hex("c0223c696d6167653e008e696d61676500"),
            pdu.copyOfRange(311, 328)
        )
        // Content-Location carries no 0x22 flag and no 0x7F quote octet.
        assertEquals(0x8E, pdu[321].toInt() and 0xFF)
        assertEquals('i'.code, pdu[322].toInt())
    }

    @Test
    fun imagePartUsesAWellKnownContentTypeAndANameParameter() {
        val pdu = sendReq(captionParts())
        // 08 value-length | A0 short-integer 0x20 (image/png) | 85 "image" NUL
        assertArrayEquals(hex("08a085696d61676500"), pdu.copyOfRange(302, 311))
        assertEquals(-1, indexOfField(partFrames(pdu)[1].header, MmsPdu.P_CHARSET))
    }

    @Test
    fun onlyTheTextPartCarriesACharsetParameter() {
        val pdu = sendReq(captionParts())
        val frames = partFrames(pdu)
        // 09 value-length | 83 short-integer 0x03 (text/plain) | 85 "text" NUL
        // | 81 EA charset 0x6A (UTF-8)
        assertArrayEquals(hex("0983857465787400") + hex("81ea"), frames[2].header.copyOfRange(0, 10))
        assertTrue(indexOfField(frames[2].header, MmsPdu.P_CHARSET) >= 0)
        assertEquals(-1, indexOfField(frames[0].header, MmsPdu.P_CHARSET))
    }

    @Test
    fun smilPartFallsBackToATextStringContentType() {
        val frames = partFrames(sendReq(captionParts()))
        // "application/smil" is not well known, so it goes out as a bare
        // text-string: no 0x7F quote, no length octet, just the octets and NUL.
        assertEquals(0x1B, frames[0].header[0].toInt() and 0xFF)
        assertEquals('a'.code, frames[0].header[1].toInt())
        assertArrayEquals(
            MmsPdu.APP_SMIL.toByteArray() + byteArrayOf(0x00) +
                byteArrayOf(MmsPdu.P_DEP_NAME.toByte()) +
                "smil.xml".toByteArray() + byteArrayOf(0x00),
            frames[0].header.copyOfRange(1, 28)
        )
    }

    // ---------------------------------------------------- well-known tables

    @Test
    fun wellKnownContentTypeIndexesMatchTheWspTable() {
        // The table is private, so it is pinned through the only observable
        // channel: the octet the encoder emits for a given type.
        assertEquals(0xB3, mediaTypeOctet(MmsPdu.MULTIPART_RELATED))
        assertEquals(0xA0, mediaTypeOctet("image/png"))
        assertEquals(0x83, mediaTypeOctet("text/plain"))
        // The OMA listing omits rows 0x0B and 0x39. Transcribing from it shifts
        // every later index by one and makes an MMSC decode the top-level type
        // as application/vnd.wap.coc.
        assertEquals(0x8B, mediaTypeOctet("multipart/*"))
        assertEquals(0xB9, mediaTypeOctet("application/vnd.wap.signed-cert"))
        // Neighbours of multipart.related, the index the whole PDU hangs on.
        assertEquals(0xB2, mediaTypeOctet("application/vnd.wap.coc"))
        assertEquals(0xB4, mediaTypeOctet("application/vnd.wap.sia"))
    }

    @Test
    fun unknownContentTypeFallsBackToATextString() {
        assertEquals('a'.code, mediaTypeOctet(MmsPdu.APP_SMIL))
        assertEquals('v'.code, mediaTypeOctet("video/quicktime"))
    }

    // ----------------------------------------------------- WSP primitives

    @Test
    fun valueLengthUsesTheShortFormBelowTheQuoteThreshold() {
        // 31 is the quote threshold, not a size cap: below it a single octet
        // holds the length, at or above it the 0x1F prefix introduces a varint.
        assertArrayEquals(byteArrayOf(0x00), write { valueLength(0) })
        assertArrayEquals(byteArrayOf(0x1E), write { valueLength(30) })
        assertArrayEquals(byteArrayOf(0x1F, 0x1F), write { valueLength(31) })
        assertArrayEquals(byteArrayOf(0x1F, 0x81.toByte(), 0x00), write { valueLength(128) })
    }

    @Test
    fun uintvarIsBigEndianSevenBitGroups() {
        assertArrayEquals(byteArrayOf(0x00), write { uintvar(0) })
        assertArrayEquals(byteArrayOf(0x7F), write { uintvar(127) })
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x00), write { uintvar(128) })
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x01), write { uintvar(129) })
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x7F), write { uintvar(255) })
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x7F), write { uintvar(16383) })
        assertArrayEquals(
            byteArrayOf(0x81.toByte(), 0x80.toByte(), 0x00),
            write { uintvar(16384) }
        )
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x7F),
            write { uintvar(2097151) }
        )
        assertArrayEquals(
            byteArrayOf(0x81.toByte(), 0x80.toByte(), 0x80.toByte(), 0x00),
            write { uintvar(2097152) }
        )
        // 2^28 - 1 still fits 4 octets; 2^28 is the first 5-octet value.
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F),
            write { uintvar(268435455) }
        )
        assertArrayEquals(
            byteArrayOf(0x81.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x00),
            write { uintvar(268435456) }
        )
        assertArrayEquals(
            byteArrayOf(
                0x81.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F
            ),
            write { uintvar(536870911) }
        )
    }

    @Test
    fun textStringQuotesOnlyWhenTheHighBitIsSet() {
        // A reader treats a leading 0x7F as a quote marker and strips one octet,
        // so 0x80 must gain a 0x7F prefix and 0x7F itself must not.
        assertArrayEquals(byteArrayOf(0x7F, 0x00), write { textString(byteArrayOf(0x7F)) })
        assertArrayEquals(
            byteArrayOf(0x7F, 0x80.toByte(), 0x00),
            write { textString(byteArrayOf(0x80.toByte())) }
        )
        assertArrayEquals(byteArrayOf(0x00), write { textString(ByteArray(0)) })
        assertArrayEquals(
            TRANSACTION_ID.toByteArray() + byteArrayOf(0),
            write { textString(TRANSACTION_ID.toByteArray()) }
        )
    }

    @Test
    fun quotedStringIsFlagThenNul() {
        assertArrayEquals(
            byteArrayOf(0x22) + "<smil>".toByteArray() + byteArrayOf(0),
            write { quotedString("<smil>".toByteArray()) }
        )
        assertArrayEquals(byteArrayOf(0x22, 0x00), write { quotedString(ByteArray(0)) })
    }

    @Test
    fun longIntegerIsOctetCountThenBigEndian() {
        // Multi-octet-integer is 1*30 OCTET, so zero still emits one data octet.
        assertArrayEquals(byteArrayOf(0x01, 0x00), write { longInteger(0) })
        assertArrayEquals(byteArrayOf(0x01, 0x7F), write { longInteger(127) })
        assertArrayEquals(byteArrayOf(0x01, 0x80.toByte()), write { longInteger(128) })
        assertArrayEquals(byteArrayOf(0x02, 0x01, 0x00), write { longInteger(256) })
        assertArrayEquals(
            byteArrayOf(0x03, 0x09, 0x3A, 0x80.toByte()),
            write { longInteger(604800) }
        )
        // The 4 -> 5 octet boundary sits at 2^32.
        assertArrayEquals(
            byteArrayOf(0x04, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            write { longInteger(4294967295L) }
        )
        assertArrayEquals(
            byteArrayOf(0x05, 0x01, 0x00, 0x00, 0x00, 0x00),
            write { longInteger(4294967296L) }
        )
    }

    @Test
    fun shortIntegerSetsTheHighBitAndRejectsWideValues() {
        assertArrayEquals(byteArrayOf(0x80.toByte()), write { shortInteger(0) })
        assertArrayEquals(byteArrayOf(0xFF.toByte()), write { shortInteger(127) })
        // A wrapped byte still parses, so a wide charset would decode as a
        // different one; the encoder rejects instead of truncating.
        assertThrows(IllegalArgumentException::class.java) { write { shortInteger(128) } }
        assertThrows(IllegalArgumentException::class.java) { write { shortInteger(-1) } }
    }

    @Test
    fun octetAndRawWriteTheLowByteOnly() {
        assertArrayEquals(
            byteArrayOf(0x0A, 0xFF.toByte()),
            write {
                octet(0x10A)
                raw(byteArrayOf(-1))
            }
        )
    }

    // -------------------------------------------------------- determinism

    @Test
    fun isDeterministicAndTakesTheTransactionIdAsInput() {
        val first = sendReq(captionParts())
        val second = sendReq(captionParts())
        assertArrayEquals(first, second)
        // Only the transaction id differs, so any clock or UUID read inside the
        // encoder would show up as a difference beyond its own field.
        val other = MmsPdu.sendReq(
            "Tdeadbeee", DATE_SECONDS, TO, captionParts(), expirySeconds = EXPIRY_SECONDS
        )
        assertEquals(TRANSACTION_ID.length, "Tdeadbeee".length)
        assertEquals('f'.code, first[11].toInt())
        assertEquals('e'.code, other[11].toInt())
        assertArrayEquals(first.copyOfRange(12, first.size), other.copyOfRange(12, other.size))
    }

    // --------------------------------------------------------------- SMIL

    @Test
    fun smilWithCaptionHasMediaAndTextElements() {
        assertEquals(
            "<smil xmlns=\"http://www.w3.org/2001/SMIL20/Language\">" +
                "<head><layout/></head><body><par dur=\"8000ms\">" +
                "<img src=\"image\"/><text src=\"text\"/></par></body></smil>",
            MmsSmil.document("image", "img", "text")
        )
    }

    @Test
    fun smilSrcAttributesCarryNoAngleBrackets() {
        // The SMIL src and the PDU Content-ID name the same part but are spelled
        // differently: bare here, bracketed on the wire.
        for (document in listOf(
            MmsSmil.document("image", "img", "text"),
            MmsSmil.document("image", "img", null)
        )) {
            assertTrue(document.contains("src=\"image\"/>"))
            assertFalse(document.contains("src=\"<"))
            assertFalse(document.contains(">\""))
        }
    }

    @Test
    fun smilWithoutCaptionDropsTheTextElement() {
        val document = MmsSmil.document("image", "img", null)
        assertEquals(
            "<smil xmlns=\"http://www.w3.org/2001/SMIL20/Language\">" +
                "<head><layout/></head><body><par dur=\"8000ms\">" +
                "<img src=\"image\"/></par></body></smil>",
            document
        )
        assertFalse(document.contains("<text"))
    }

    @Test
    fun smilEscapesMarkupInSources() {
        val document = MmsSmil.document("a&b", "img", "<x>")
        assertTrue(document.contains("src=\"a&amp;b\""))
        assertTrue(document.contains("src=\"&lt;x&gt;\""))
    }

    @Test
    fun pduContentIdIsBracketedWhileTheSmilSrcIsNot() {
        val image = partFrames(sendReq(captionParts()))[1]
        // C0 22 "<image>" NUL | 8E "image" NUL
        assertArrayEquals(
            hex("c0223c696d6167653e00") + byteArrayOf(0x8E.toByte()) +
                "image".toByteArray() + byteArrayOf(0x00),
            image.header.copyOfRange(image.header.size - 17, image.header.size)
        )
        assertTrue(SMIL_WITH_CAPTION.contains("src=\"image\""))
        assertFalse(SMIL_WITH_CAPTION.contains("src=\"<image>\""))
    }

    // ------------------------------------------------------------- helpers

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.UTF_8)

    private inline fun write(block: MmsPdu.Enc.() -> Unit): ByteArray =
        MmsPdu.Enc().apply(block).toBytes()

    private fun indexOfField(header: ByteArray, code: Int): Int =
        header.indexOfFirst { (it.toInt() and 0xFF) == code }

    private class PartFrame(
        val offset: Int,
        val headerLength: Long,
        val dataLength: Long,
        val header: ByteArray
    ) {
        val length: Int get() = varintLength(headerLength) + varintLength(dataLength) +
            headerLength.toInt() + dataLength.toInt()
    }

    private fun partFrames(pdu: ByteArray, bodyStart: Int = bodyOffset(pdu)): List<PartFrame> {
        val (count, first) = readVarint(pdu, bodyStart)
        val frames = mutableListOf<PartFrame>()
        var at = first
        repeat(count.toInt()) {
            val (headerLength, afterHeader) = readVarint(pdu, at)
            val (dataLength, dataStart) = readVarint(pdu, afterHeader)
            frames.add(
                PartFrame(
                    at,
                    headerLength,
                    dataLength,
                    pdu.copyOfRange(dataStart, dataStart + headerLength.toInt())
                )
            )
            at = dataStart + headerLength.toInt() + dataLength.toInt()
        }
        assertEquals("part framing must consume the whole body", pdu.size.toLong(), at.toLong())
        return frames
    }

    /**
     * WSP varints are big-endian: the first octet carries the *high* 7-bit
     * group, so groups are folded left to right rather than shifted in.
     */
    private fun readVarint(pdu: ByteArray, offset: Int): Pair<Long, Int> {
        var at = offset
        var value = 0L
        var octets = 0
        while (true) {
            val octet = pdu[at].toInt() and 0xFF
            value = (value shl 7) or (octet.toLong() and 0x7F)
            octets++
            at++
            if ((octet and 0x80) == 0) break
        }
        assertTrue("varint should fit in a Long", octets <= 8)
        return value to at
    }

    /**
     * Offset of the part count, i.e. just past the header block. Derived by
     * walking the headers rather than hardcoded: the Content-Type value-length
     * varies with the media types, so a fixed offset silently misreads any
     * shape other than the golden fixtures.
     */
    private fun bodyOffset(pdu: ByteArray): Int {
        var at = 0
        while (at < pdu.size) {
            val field = pdu[at].toInt() and 0xFF
            at++
            when (field) {
                // Only the fields MmsPdu.sendReq can emit; anything else means
                // the emit order changed and this walk needs revisiting.
                0x8C, 0x8A, 0x8F, 0x86, 0x90, 0x8D -> at += 1
                0x98 -> at = textEnd(pdu, at)
                0x97, 0x96 -> at = encodedStringEnd(pdu, at)
                0x89, 0x88 -> at = valueLengthEnd(pdu, at)
                0x85 -> at = longIntegerEnd(pdu, at)
                0x84 -> return valueLengthEnd(pdu, at)
                else -> throw AssertionError("unexpected header field 0x${field.toString(16)} at ${at - 1}")
            }
        }
        throw AssertionError("no content-type field")
    }

    private fun textEnd(pdu: ByteArray, at: Int): Int {
        var i = at
        if ((pdu[i].toInt() and 0xFF) == 0x7F) i++
        while (i < pdu.size && pdu[i].toInt() != 0) i++
        return i + 1
    }

    /** Value-length prefix, then the value it counts. */
    private fun valueLengthEnd(pdu: ByteArray, at: Int): Int {
        val first = pdu[at].toInt() and 0xFF
        val (length, after) = if (first < 31) first.toLong() to at + 1
        else readVarint(pdu, at + 1)
        return after + length.toInt()
    }

    /** Value-length, then charset short-integer, then the text-string. */
    private fun encodedStringEnd(pdu: ByteArray, at: Int): Int {
        val first = pdu[at].toInt() and 0xFF
        return if (first < 31) textEnd(pdu, at + 2) else {
            val (length, after) = readVarint(pdu, at + 1)
            textEnd(pdu, after + length.toInt() + 1)
        }
    }

    private fun longIntegerEnd(pdu: ByteArray, at: Int): Int {
        val octets = pdu[at].toInt() and 0xFF
        return at + 1 + octets
    }

    /** The first octet of a part's content-type value, i.e. after its length. */
    private fun mediaTypeOctet(contentType: String): Int {
        val pdu = sendReq(
            listOf(
                MmsPdu.Part("application/smil", "smil.xml", "smil", null, SMIL_NO_CAPTION.toByteArray()),
                MmsPdu.Part(contentType, "f", "c", null, byteArrayOf(0x01))
            )
        )
        val header = partFrames(pdu)[1].header
        assertTrue("value-length should fit one octet", (header[0].toInt() and 0xFF) < 31)
        return header[1].toInt() and 0xFF
    }

    private companion object {
        const val TRANSACTION_ID = "Tdeadbeef"
        const val DATE_SECONDS = 1700000000L
        const val TO = "+15551239999"
        const val EXPIRY_SECONDS = 604800L
        const val PLMN = "/TYPE=PLMN"

        val SMIL_WITH_CAPTION = MmsSmil.document("image", "img", "text")
        val SMIL_NO_CAPTION = MmsSmil.document("image", "img", null)

        const val PNG_HEX =
            "89504e470d0a1a0a0000000d4948445200000002000000020802000000fdd49a73" +
            "0000001049444154789c63f8cfc000440c100a001fee03fd8b5f14d400000000" +
            "49454e44ae426082"

        const val GOLDEN_CAPTION =
            "8c8098546465616462656566008d9285046553f1008901819718ea2b31353535313233393939392f545950453d50" +
            "4c4d4e008a8088058103093a808f8186819081841bb38a3c736d696c3e00896170706c69636174696f6e2f736d69" +
            "6c00032f811b1b6170706c69636174696f6e2f736d696c0085736d696c2e786d6c00c0223c736d696c3e008e736d" +
            "696c2e786d6c003c736d696c20786d6c6e733d22687474703a2f2f7777772e77332e6f72672f323030312f534d49" +
            "4c32302f4c616e6775616765223e3c686561643e3c6c61796f75742f3e3c2f686561643e3c626f64793e3c706172" +
            "206475723d22383030306d73223e3c696d67207372633d22696d616765222f3e3c74657874207372633d22746578" +
            "74222f3e3c2f7061723e3c2f626f64793e3c2f736d696c3e1a4908a085696d61676500c0223c696d6167653e008e" +
            "696d6167650089504e470d0a1a0a0000000d4948445200000002000000020802000000fdd49a7300000010494441" +
            "54789c63f8cfc000440c100a001fee03fd8b5f14d40000000049454e44ae4260821905098385746578740081eac0" +
            "223c746578743e008e746578740068656c6c6f"
        const val GOLDEN_NO_CAPTION =
            "8c8098546465616462656566008d9285046553f1008901819718ea2b31353535313233393939392f545950453d50" +
            "4c4d4e008a8088058103093a808f8186819081841bb38a3c736d696c3e00896170706c69636174696f6e2f736d69" +
            "6c00022f81091b6170706c69636174696f6e2f736d696c0085736d696c2e786d6c00c0223c736d696c3e008e736d" +
            "696c2e786d6c003c736d696c20786d6c6e733d22687474703a2f2f7777772e77332e6f72672f323030312f534d49" +
            "4c32302f4c616e6775616765223e3c686561643e3c6c61796f75742f3e3c2f686561643e3c626f64793e3c706172" +
            "206475723d22383030306d73223e3c696d67207372633d22696d616765222f3e3c2f7061723e3c2f626f64793e3c" +
            "2f736d696c3e1a4908a085696d61676500c0223c696d6167653e008e696d6167650089504e470d0a1a0a0000000d" +
            "4948445200000002000000020802000000fdd49a730000001049444154789c63f8cfc000440c100a001fee03fd8b" +
            "5f14d40000000049454e44ae426082"
    }
}

private fun varintLength(value: Long): Int {
    var octets = 1
    var rest = value
    while (rest > 0x7F) {
        octets++
        rest = rest ushr 7
    }
    return octets
}
