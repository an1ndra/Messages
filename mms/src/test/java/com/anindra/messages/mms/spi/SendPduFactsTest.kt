package com.anindra.messages.mms.spi

import com.anindra.messages.mms.FittedAttachment
import com.anindra.messages.mms.SendAddressSource
import com.anindra.messages.mms.SendHeaderPolicy
import com.anindra.messages.mms.SendReqBuilder
import com.anindra.messages.mms.SendReqOutcome
import com.anindra.messages.mms.SendReqRequest
import com.anindra.messages.mms.Wire
import com.anindra.messages.mms.parseOrFail
import com.anindra.messages.mms.profileOf
import com.anindra.messages.mms.net.CarrierProfile
import com.anindra.messages.mms.pdu.EncodedStringValue
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Why these tests matter: a carrier that answers 500 to a submission looks
 * identical to a broken attachment or a mis-addressed message unless the report
 * says what was on the wire. The facts are read back through the composer's own
 * helpers, so what is asserted here is what [PduComposer] would write, not a
 * second opinion about it.
 */
class SendPduFactsTest {

    private val now = 1_700_000_000L

    private fun built(
        sender: SendAddressSource = SendAddressSource { null },
        addresses: List<String> = listOf("+15551230000"),
        caption: String = "",
    ): Pdu {
        val outcome = SendReqBuilder.build(
            SendReqRequest(
                addresses = addresses,
                caption = caption,
                attachments = listOf(FittedAttachment("image/jpeg", ByteArray(64))),
                profile = profileOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to 300_000),
                subscriptionId = Wire.SUBSCRIPTION_ID,
                headers = SendHeaderPolicy.DEFAULT,
            ),
            sender,
            now,
        )
        return (outcome as SendReqOutcome.Built).pdu
    }

    private fun factsOf(pdu: Pdu): SendPduFacts =
        SendPduFacts.of(pdu, requireNotNull(PduComposer.compose(pdu)).size)

    @Test
    fun aSendWithNoKnownNumberReportsTheInsertAddressToken() {
        // Most carriers fill the sender in from the SIM. A report that showed a
        // blank From would hide the difference between that and a wrong number.
        assertEquals(SendPduFacts.INSERT_ADDRESS_TOKEN, factsOf(built()).from)
    }

    @Test
    fun aKnownLineStillReportsTheInsertAddressToken() {
        val pdu = built(sender = SendAddressSource { "+15550001111" })
        assertEquals(SendPduFacts.INSERT_ADDRESS_TOKEN, factsOf(pdu).from)
    }

    @Test
    fun recipientsAreReportedAsTheCarrierWillParseThem() {
        // The qualifier is added at compose time and stripped again on read, so
        // the stored header alone would understate what the MMSC received.
        val facts = factsOf(built(addresses = listOf("+15551230000", "+15559998888")))
        assertEquals(listOf("+15551230000/TYPE=PLMN", "+15559998888/TYPE=PLMN"), facts.recipients)
    }

    @Test
    fun onlyASubmissionQualifiesPhoneNumbersAsRecipients() {
        // A received PDU names its sender the same way; read back through the
        // composer it must not gain a qualifier the wire never carried.
        val received = Pdu(MessageType.RETRIEVE_CONF).apply {
            headers.addEncoded(HeaderField.TO, EncodedStringValue.utf8("+15551230000"))
        }
        assertEquals(listOf("+15551230000"), SendPduFacts.of(received, 10).recipients)
    }

    @Test
    fun everyPartIsReportedWithItsIdentifiers() {
        val facts = factsOf(built(caption = "hello"))
        assertEquals(listOf("smil.xml", "image", "text"), facts.parts.map { it.name })
        assertEquals(
            listOf("application/smil", "image/jpeg", "text/plain"),
            facts.parts.map { it.contentType },
        )
        assertEquals(listOf("smil.xml", "image", "text"), facts.parts.map { it.contentId })
        // Every part carries its own content-location, as in the platform
        // composer's submission.
        assertEquals(listOf("smil.xml", "image", "text"), facts.parts.map { it.contentLocation })
        assertEquals(64, facts.parts[1].bytes)
    }

    @Test
    fun theHeaderOrderOpensWithTheTypeAndClosesWithTheContentType() {
        val facts = factsOf(built())
        assertEquals(
            listOf(HeaderField.MESSAGE_TYPE, HeaderField.TRANSACTION_ID, HeaderField.MMS_VERSION),
            facts.headerCodes.take(3),
        )
        assertEquals(HeaderField.CONTENT_TYPE, facts.headerCodes.last())
    }

    @Test
    fun theHeaderOrderListsEveryFieldThePduCarries() {
        val facts = factsOf(built())
        assertTrue(!facts.headerCodes.contains(HeaderField.MESSAGE_SIZE))
        assertTrue(facts.headerCodes.contains(HeaderField.DATE))
        assertTrue(facts.headerCodes.contains(HeaderField.FROM))
        assertTrue(facts.headerCodes.contains(HeaderField.TO))
        assertTrue(facts.headerCodes.contains(HeaderField.MMS_VERSION))
        // One entry per field code, so a repeated To cannot be counted twice.
        assertEquals(facts.headerCodes.size, facts.headerCodes.distinct().size)
    }

    @Test
    fun anOutgoingSubmissionDoesNotDeclareAMessageSize() {
        val facts = factsOf(built())
        assertNull(facts.declaredMessageSize)
        assertTrue(facts.pduSize > 0)
    }

    @Test
    fun aPduWithNoMessageSizeSaysSoRatherThanShowingZero() {
        val received = parseOrFail(Wire.retrieveConf("text/plain" to "hello"))
        assertNull(SendPduFacts.of(received, 10).declaredMessageSize)
    }

    @Test
    fun thePrintedFactsCarryEveryField() {
        val fields = factsOf(built()).fields()
        assertEquals(
            listOf("size", "from", "to", "msgSize", "parts", "headers"),
            fields.keys.toList(),
        )
        assertEquals("insert-address-token", fields["from"])
        assertEquals("+15551230000/TYPE=PLMN", fields["to"])
        assertTrue(fields.getValue("parts").startsWith("smil.xml/application/smil/cid=smil.xml/cl=smil.xml/"))
        assertTrue(fields.getValue("headers").startsWith("8c.98.8d.85.89.97."))
    }
}
