package com.anindra.messages.mms

import com.anindra.messages.mms.net.CarrierProfile
import com.anindra.messages.mms.pdu.ContentTypes
import com.anindra.messages.mms.pdu.EncodedStringValue
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduComposer
import com.anindra.messages.mms.spi.BudgetPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The builder's output is the only thing standing between a mis-addressed or
 * over-budget message and the carrier, so these pin the parts that matter on the
 * wire rather than only on the object.
 */
class SendReqBuilderTest {

    private val now = 1_700_000_000L
    private val noSender = SendAddressSource { null }

    private fun request(
        addresses: List<String> = listOf("+15551230000", "+15559998888"),
        caption: String = "hello",
        attachmentBytes: ByteArray = ByteArray(64),
        profile: CarrierProfile = profileOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to 300_000),
        headers: SendHeaderPolicy = SendHeaderPolicy.DEFAULT,
    ) = SendReqRequest(
        addresses = addresses,
        caption = caption,
        attachments = listOf(FittedAttachment("image/jpeg", attachmentBytes)),
        profile = profile,
        subscriptionId = Wire.SUBSCRIPTION_ID,
        headers = headers,
    )

    private fun built(
        addresses: List<String> = listOf("+15551230000", "+15559998888"),
        caption: String = "hello",
        attachmentBytes: ByteArray = ByteArray(64),
        profile: CarrierProfile = profileOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to 300_000),
        headers: SendHeaderPolicy = SendHeaderPolicy.DEFAULT,
        sender: SendAddressSource = noSender,
    ): Pdu {
        val outcome = SendReqBuilder.build(
            request(addresses, caption, attachmentBytes, profile, headers),
            sender,
            now,
        )
        assertTrue("expected a built PDU, got $outcome", outcome is SendReqOutcome.Built)
        return (outcome as SendReqOutcome.Built).pdu
    }

    private fun composed(pdu: Pdu): ByteArray {
        val bytes = PduComposer.compose(pdu)
        return requireNotNull(bytes) { "expected ${pdu.messageType} to compose" }
    }

    @Test
    fun twoRecipientsBecomeTwoToHeaders() {
        val pdu = built()
        assertEquals(listOf("+15551230000", "+15559998888"), pdu.to.map { it.text })
    }

    @Test
    fun twoRecipientsRepeatTheToFieldCodeOnTheWire() {
        val bytes = composed(built())
        // One field code per recipient: packing them into one header would be a
        // different field, and the group would be lost at the MMSC.
        assertEquals(2, bytes.count { it == HeaderField.TO.toByte() })
    }

    @Test
    fun aGroupSendRoundTripsThroughTheWire() {
        val parsed = parseOrFail(composed(built()))
        assertEquals(MessageType.SEND_REQ, parsed.messageType)
        assertEquals(listOf("+15551230000", "+15559998888"), parsed.to.map { it.text })
        assertEquals(3, parsed.body?.size)
        assertEquals("hello", parsed.body?.textContent())
    }

    @Test
    fun duplicateAndBlankRecipientsAreDropped() {
        val pdu = built(addresses = listOf("+15551230000", "  +15551230000 ", "", "  ", "+15559998888"))
        assertEquals(listOf("+15551230000", "+15559998888"), pdu.to.map { it.text })
    }

    @Test
    fun theCaptionBecomesATextPlainPart() {
        val body = built().body!!
        val text = body.text
        assertNotNull(text)
        assertEquals(ContentTypes.normalize("text/plain"), ContentTypes.normalize(text!!.contentType!!))
        assertEquals("hello", body.textContent())
    }

    @Test
    fun smilLeadsTheBody() {
        val body = built().body!!
        assertEquals(SendReqBuilder.SMIL_PART_INDEX, 0)
        assertTrue("part 0 should be the SMIL", body.partAt(0)!!.isSmil)
        assertEquals(Smil, String(body.partAt(0)!!.data!!))
    }

    @Test
    fun aMessageWithoutACaptionHasNoTextPart() {
        val body = built(caption = "   ").body!!
        assertEquals(SendReqBuilder.partCount("   "), body.size)
        assertEquals(null, body.text)
    }

    @Test
    fun aTextOnlyGroupMessageStillBuildsItsPresentation() {
        // A group text leaves as one MMS with no attachment (#448); it is a text
        // part and a SMIL slide, addressed to everyone.
        val outcome = SendReqBuilder.build(request().copy(attachments = emptyList()), noSender, now)
        assertTrue("expected a built PDU, got $outcome", outcome is SendReqOutcome.Built)
        val pdu = (outcome as SendReqOutcome.Built).pdu
        val body = pdu.body!!
        assertEquals(2, body.size)
        assertTrue("the SMIL must still lead", body.partAt(0)!!.isSmil)
        assertEquals("hello", body.textContent())
        assertEquals(listOf("+15551230000", "+15559998888"), pdu.to.map { it.text })
    }

    @Test
    fun aTextOnlyMessageWithNothingToLayOutIsStillRefused() {
        val outcome = SendReqBuilder.build(
            request().copy(attachments = emptyList(), caption = "   "), noSender, now,
        )
        assertEquals(SendReqOutcome.NoPresentation, outcome)
    }

    @Test
    fun aTextOnlyMessageStillObeysTheCarrierGroupCheck() {
        val outcome = SendReqBuilder.build(
            request(profile = profileOf(CarrierProfile.KEY_GROUP_MMS_ENABLED to false))
                .copy(attachments = emptyList()),
            noSender,
            now,
        )
        assertEquals(SendReqOutcome.GroupMmsUnsupported, outcome)
    }

    @Test
    fun partCountGrowsWithEachAttachment() {
        assertEquals(2, SendReqBuilder.partCount(""))
        assertEquals(3, SendReqBuilder.partCount("cap"))
        assertEquals(4, SendReqBuilder.partCount("", 3))
        assertEquals(5, SendReqBuilder.partCount("cap", 3))
    }

    @Test
    fun everyPictureGetsItsOwnSlideAndItsOwnPart() {
        val pdu = multiBuilt(3)
        val body = pdu.body!!
        assertEquals(SendReqBuilder.partCount("hello", 3), body.size)
        val media = body.attachments
        assertEquals(3, media.size)
        assertEquals(
            "each picture must be addressable on its own",
            3,
            media.map { it.name }.distinct().size,
        )
        val smil = String(body.smil!!.data!!)
        // One slide per picture: all three in one par would show as one.
        assertEquals(3, Regex("<par ").findAll(smil).count())
        val sources = Regex("src=\"([^\"]+)\"").findAll(smil).map { it.groupValues[1] }.toSet()
        assertTrue(
            "every SMIL src must name a part that was written: $sources",
            sources.containsAll(media.map { it.name }),
        )
    }

    private fun multiBuilt(count: Int): Pdu {
        val request = SendReqRequest(
            addresses = listOf("+15551230000"),
            caption = "hello",
            attachments = List(count) {
                FittedAttachment("image/jpeg", ByteArray(16) { b -> (b + it).toByte() })
            },
            profile = profileOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to 300_000),
            subscriptionId = Wire.SUBSCRIPTION_ID,
            headers = SendHeaderPolicy.DEFAULT,
        )
        val outcome = SendReqBuilder.build(request, noSender, now)
        assertTrue("expected a built PDU, got $outcome", outcome is SendReqOutcome.Built)
        return (outcome as SendReqOutcome.Built).pdu
    }

    @Test
    fun theMessageDeclaresAMultipartContentType() {
        val pdu = built()
        assertTrue(ContentTypes.isMultipart(pdu.contentType!!))
    }

    @Test
    fun theSizeAndPresentationHeadersComeFromTheRequest() {
        val headers = SendHeaderPolicy(
            messageClass = HeaderField.MESSAGE_CLASS_INFORMATIONAL,
            expirySeconds = 3_600L,
            priority = HeaderField.PRIORITY_HIGH,
            deliveryReport = HeaderField.VALUE_YES,
            readReport = HeaderField.VALUE_NO,
        )
        val pdu = built(headers = headers)
        assertEquals(pdu.body!!.totalDataBytes(), pdu.messageSize)
        assertEquals(HeaderField.MESSAGE_CLASS_INFORMATIONAL, pdu.messageClassOctet)
        assertEquals(3_600L, pdu.expirySeconds)
        assertEquals(HeaderField.PRIORITY_HIGH, pdu.headers.octetOrNull(HeaderField.PRIORITY))
        assertEquals(HeaderField.VALUE_YES, pdu.headers.octetOrNull(HeaderField.DELIVERY_REPORT))
        assertEquals(HeaderField.VALUE_NO, pdu.headers.octetOrNull(HeaderField.READ_REPORT))
    }

    @Test
    fun theCarrierDecidesWhetherAGroupSendIsAllowed() {
        val outcome = SendReqBuilder.build(
            request(profile = profileOf(CarrierProfile.KEY_GROUP_MMS_ENABLED to false)),
            noSender,
            now,
        )
        assertEquals(SendReqOutcome.GroupMmsUnsupported, outcome)
    }

    @Test
    fun oneRecipientIsAllowedEvenWhereGroupsAreNot() {
        val pdu = built(
            addresses = listOf("+15551230000"),
            profile = profileOf(CarrierProfile.KEY_GROUP_MMS_ENABLED to false),
        )
        assertEquals(listOf("+15551230000"), pdu.to.map { it.text })
    }

    @Test
    fun noUsableRecipientIsNotABuiltPdu() {
        assertEquals(SendReqOutcome.NoRecipients, SendReqBuilder.build(request(addresses = emptyList()), noSender, now))
    }

    @Test
    fun aMediaTypeWithNoSmilElementIsRefusedRatherThanSentBlank() {
        val outcome = SendReqBuilder.build(
            request().copy(attachments = listOf(FittedAttachment("application/octet-stream", ByteArray(8))), caption = ""),
            noSender,
            now,
        )
        assertEquals(SendReqOutcome.NoPresentation, outcome)
    }

    @Test
    fun withoutAKnownSenderTheFromHeaderIsTheInsertAddressToken() {
        val parsed = parseOrFail(composed(built()))
        assertEquals(EncodedStringValue.insertAddressToken(), parsed.from)
    }

    @Test
    fun aKnownSenderIsWrittenOutAsTheFromAddress() {
        val pdu = built(sender = SendAddressSource { "+15550001111" })
        val parsed = parseOrFail(composed(pdu))
        assertEquals("+15550001111", parsed.from?.text)
    }

    @Test
    fun twoSendsInTheSameSecondGetDifferentTransactionIds() {
        val first = built().transactionId
        val second = SendReqBuilder.build(
            request(),
            noSender,
            now,
        ).let { (it as SendReqOutcome.Built).pdu.transactionId }
        assertTrue("two sends in one second must not share a transaction id", first != second)
    }

    @Test
    fun theComposedPduStaysInsideTheCarrierCap() {
        val cap = 40_000
        val caption = "a caption"
        val profile = profileOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to cap)
        val budget = BudgetPolicy.attachmentBudget(
            carrierMaxMessageSize = cap,
            captionBytes = caption.toByteArray(Charsets.UTF_8).size,
            partCount = SendReqBuilder.partCount(caption),
        )
        val bytes = composed(
            built(caption = caption, attachmentBytes = ByteArray(budget.toInt()), profile = profile),
        )
        assertTrue(
            "composed ${bytes.size}B over a ${cap}B cap",
            bytes.size.toLong() <= cap.toLong(),
        )
    }

    private val Smil = "<smil xmlns=\"http://www.w3.org/2001/SMIL20/Language\">" +
        "<head><layout/></head><body><par dur=\"8000ms\">" +
        "<img src=\"image\"/><text src=\"text\"/>" +
        "</par></body></smil>"
}