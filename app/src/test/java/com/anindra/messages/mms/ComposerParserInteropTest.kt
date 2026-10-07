package com.anindra.messages.mms

import com.anindra.messages.mms.pdu.CharacterSets
import com.anindra.messages.mms.pdu.ContentTypes
import com.anindra.messages.mms.pdu.EncodedStringValue
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduBody
import com.anindra.messages.mms.pdu.PduComposer
import com.anindra.messages.mms.pdu.PduPart
import com.anindra.messages.mms.pdu.PduParser
import com.google.android.mms.pdu_alt.PduBody as VendoredBody
import com.google.android.mms.pdu_alt.PduComposer as VendoredComposer
import com.google.android.mms.pdu_alt.PduParser as VendoredParser
import com.google.android.mms.pdu_alt.PduPart as VendoredPart
import com.google.android.mms.pdu_alt.SendReq as VendoredSendReq
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The two PDU stacks this repo carries must interoperate: the new `:mms`
 * composer is only correct if the production reference parser can read what it
 * writes, and vice versa, because carriers and the platform's own stack are
 * built on the reference's conventions.
 *
 * The two conventions a same-repo round trip cannot see — and that both
 * directions here pin — are the ones the new stack originally got wrong: a
 * uintvar must carry its most significant 7-bit group first (WAP-230 §3.1),
 * and X-Mms-Content-Type must close the header block, because the reference
 * parser stops reading headers at it.
 *
 * The reference composer only opens its Context's resolver for a part that
 * carries a data Uri rather than inline bytes, so an inline-data fixture runs
 * in a plain JVM test with no emulator.
 */
class ComposerParserInteropTest {

    /** A part body over the 127-byte single-octet length boundary. */
    private val bigData = ByteArray(300) { ((it * 7) % 251).toByte() }

    private fun newStackPdu(transactionId: String): Pdu = Pdu(MessageType.SEND_REQ).apply {
        headers.setContentType(ContentTypes.MULTIPART_RELATED)
        headers.setText(HeaderField.TRANSACTION_ID, transactionId)
        from = EncodedStringValue(CharacterSets.UTF_8, "+15551230000")
        headers.addEncoded(HeaderField.TO, EncodedStringValue(CharacterSets.UTF_8, "+15559998888"))
        headers.setLong(HeaderField.DATE, 1_700_000_000L)
        body = PduBody().also { body ->
            body.add(PduPart().apply {
                contentType = "text/plain"
                contentLocation = "big.txt"
                contentId = "<big.txt>"
                charset = CharacterSets.UTF_8
                data = bigData
            })
        }
    }

    private fun vendoredPdu(transactionId: String): VendoredSendReq = VendoredSendReq().apply {
        setTransactionId(transactionId.toByteArray(Charsets.US_ASCII))
        from = com.google.android.mms.pdu_alt.EncodedStringValue("+15551230000")
        addTo(com.google.android.mms.pdu_alt.EncodedStringValue("+15559998888"))
        date = 1_700_000_000L
        messageClass = com.google.android.mms.pdu_alt.PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray()
        body = VendoredBody().also { body ->
            body.addPart(VendoredPart().apply {
                contentType = "text/plain".toByteArray()
                contentLocation = "big.txt".toByteArray()
                setContentId("<big.txt>".toByteArray())
                setData(bigData)
            })
        }
    }

    @Test
    fun theReferenceParserReadsWhatTheNewComposerWrites() {
        val bytes = PduComposer.compose(newStackPdu("interop-01"))
        assertNotNull("the new composer refused a valid M-Send.req", bytes)

        val parsed = VendoredParser(bytes!!).parse()
        assertNotNull(
            "the reference parser could not read the new composer's output",
            parsed,
        )
        val out = parsed as VendoredSendReq
        assertEquals(128, out.messageType)
        assertEquals("interop-01", String(out.transactionId))
        assertEquals("+15559998888", out.to.first().string)
        val part = out.body.getPart(0)
        assertEquals("big.txt", String(part.contentLocation))
        assertArrayEquals(bigData, part.data)
    }

    /**
     * The reference composer only opens its Context's resolver for a part that
     * carries a data Uri rather than inline bytes; an inline-data fixture
     * never touches it, so a context with no resolver composes fine in a
     * plain JVM test with no emulator.
     */
    private class NullResolverContext : android.content.ContextWrapper(null) {
        override fun getContentResolver(): android.content.ContentResolver? = null
    }

    @Test
    fun theNewParserReadsWhatTheReferenceComposerWrites() {
        val bytes = VendoredComposer(NullResolverContext(), vendoredPdu("interop-02")).make()
        assertEquals("the reference composer produced nothing", true, bytes.isNotEmpty())

        val parsed = PduParser(bytes).parse()
        assertNotNull("the new parser could not read the reference output", parsed)
        val out = parsed!!
        assertEquals(MessageType.SEND_REQ, out.messageType)
        assertEquals("interop-02", out.headers.textOrNull(HeaderField.TRANSACTION_ID))
        // The reference writes a phone recipient with the /TYPE=PLMN suffix and
        // reads it back the same way; the app's own peer rule
        // (MmsSupport.phoneAddress) strips that suffix when attributing a
        // conversation, so the stacks agree on the number, differing only in
        // spelling.
        assertEquals(listOf("+15559998888/TYPE=PLMN"), out.to.map { it.text })
        val part = out.body!!.parts()[0]
        assertEquals("big.txt", part.contentLocation)
        assertArrayEquals(bigData, part.data)
    }
}
