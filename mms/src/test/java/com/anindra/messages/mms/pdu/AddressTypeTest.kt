package com.anindra.messages.mms.pdu

import com.anindra.messages.mms.FittedAttachment
import com.anindra.messages.mms.SendAddressSource
import com.anindra.messages.mms.SendHeaderPolicy
import com.anindra.messages.mms.SendReqBuilder
import com.anindra.messages.mms.SendReqOutcome
import com.anindra.messages.mms.SendReqRequest
import com.anindra.messages.mms.Wire
import com.anindra.messages.mms.net.CarrierProfile
import com.anindra.messages.mms.profileOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A submission names its phone recipients `number/TYPE=PLMN`. A carrier's MMSC
 * refuses a bare number, and the qualifier is not something to leave in a
 * stored address either, so it is added on the way out and taken off on the way
 * in.
 */
class AddressTypeTest {
    private fun value(text: String) = EncodedStringValue(CharacterSets.UTF_8, text)

    @Test
    fun aPhoneNumberGetsTheQualifierOnTheWire() {
        assertEquals("+15551234567/TYPE=PLMN", value("+15551234567").withPhoneAddressType().text)
        assertEquals("555 123-4567/TYPE=PLMN", value("555 123-4567").withPhoneAddressType().text)
    }

    @Test
    fun emailsAndQualifiedOrAlphanumericAddressesAreLeftAlone() {
        assertEquals("a@b.c", value("a@b.c").withPhoneAddressType().text)
        assertEquals("+1555/TYPE=PLMN", value("+1555/TYPE=PLMN").withPhoneAddressType().text)
        assertEquals("+1555/type=plmn", value("+1555/type=plmn").withPhoneAddressType().text)
        assertEquals("INFO", value("INFO").withPhoneAddressType().text)
    }

    @Test
    fun theQualifierIsRemovedAgainWhenReading() {
        assertEquals("+15551234567", value("+15551234567/TYPE=PLMN").withoutPhoneAddressType().text)
        assertEquals("+15551234567", value("+15551234567/TYPE=PLMN".lowercase()).withoutPhoneAddressType().text)
        assertEquals("+15551234567", value("+15551234567").withoutPhoneAddressType().text)
    }

    @Test
    fun onlyASendRequestQualifiesItsRecipients() {
        // A PDU whose addresses came from the carrier is already qualified if the
        // carrier qualifies them; adding it again would double the suffix on a
        // header this app only reads, so the qualifier is a send-request-only rule.
        val incoming = Pdu(MessageType.READ_REC_IND).apply {
            headers.setEncodedList(HeaderField.TO, listOf(EncodedStringValue.utf8("+15559998888")))
            headers.setEncoded(HeaderField.FROM, EncodedStringValue.utf8("+15551230000"))
            headers.setText(HeaderField.MESSAGE_ID, "M-1")
            headers.setOctet(HeaderField.READ_STATUS, 0x80)
            headers.setLong(HeaderField.DATE, 1_700_000_000L)
        }
        val wire = PduComposer.compose(incoming)!!
        assertFalse(
            "a received PDU must not gain /TYPE=PLMN on the way out",
            String(wire, Charsets.ISO_8859_1).contains("TYPE=PLMN"),
        )
    }

    @Test
    fun aSubmissionCarriesTheQualifierAndReadsBackBare() {
        val built = SendReqBuilder.build(
            SendReqRequest(
                addresses = listOf("+15559998888", "someone@example.com"),
                caption = "hello",
                attachment = FittedAttachment("image/jpeg", ByteArray(64)),
                profile = profileOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to 300_000),
                subscriptionId = Wire.SUBSCRIPTION_ID,
                headers = SendHeaderPolicy.DEFAULT,
            ),
            SendAddressSource { null },
            1_700_000_000L,
        )
        assertTrue("expected a built PDU, got $built", built is SendReqOutcome.Built)

        val wire = PduComposer.compose((built as SendReqOutcome.Built).pdu)!!
        assertTrue(
            "the phone recipient must reach the wire qualified",
            String(wire, Charsets.ISO_8859_1).contains("+15559998888/TYPE=PLMN"),
        )
        assertFalse(
            "an e-mail recipient must not be qualified as a phone number",
            String(wire, Charsets.ISO_8859_1).contains("someone@example.com/TYPE=PLMN"),
        )

        // A stored address is the bare number again, which is what keeps the
        // thread keyed on the same value every other MMS arrives with.
        val parsed = PduParser(wire, nowSeconds = { 1_700_000_000L }).parse()!!
        assertEquals(listOf("+15559998888", "someone@example.com"), parsed.to.map { it.text })
    }
}