package com.anindra.messages.share

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A share intent carries its body as text that another app encoded, and both
 * ways it gets that wrong are reproducible here (issue #304).
 */
class ShareTextTest {

    private val receipt = "مبلغ 100,000 ریال به کارت 5022 شماره تراکنش 1405/07/15"
    private val receiptEncoded = encode(receipt)

    /** `receipt` percent-encoded the way an Android sender would, via `Uri.encode`:
     *  only ASCII alphanumerics stay literal, every byte above `7F` becomes `%XX`. */
    private fun encode(value: String): String = buildString {
        for (byte in value.toByteArray(StandardCharsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            if (code <= 0x7F && (code.toChar().isLetterOrDigit() || code.toChar() in "-_.~")) {
                append(code.toChar())
            } else {
                append('%').append("%02X".format(code))
            }
        }
    }

    @Test
    fun readsBodyFromQuery() {
        assertEquals(receipt, ShareText.bodyFromQuery("body=$receiptEncoded"))
    }

    @Test
    fun readsBodyAlongsideOtherQueryParameters() {
        val query = "address=%2B15551230000&body=$receiptEncoded"
        assertEquals(receipt, ShareText.bodyFromQuery(query))
    }

    @Test
    fun keepsAnUnencodedBodyIntact() {
        assertEquals(receipt, ShareText.bodyFromQuery("body=$receipt"))
    }

    @Test
    fun returnsNullWithoutABodyParameter() {
        assertNull(ShareText.bodyFromQuery(null))
        assertNull(ShareText.bodyFromQuery(""))
        assertNull(ShareText.bodyFromQuery("address=%2B15551230000"))
        assertNull(ShareText.bodyFromQuery("subject=Receipt"))
    }

    @Test
    fun returnsNullForAnEmptyBody() {
        assertNull(ShareText.bodyFromQuery("body="))
        assertNull(ShareText.bodyFromQuery("body=%20%20"))
    }

    /** The body is a `?body=` value that survives on the *first* decode as
     *  `%25D9%2585`, i.e. still escaped: one pass would send that literally. */
    @Test
    fun decodesABodyThatWasEscapedTwice() {
        assertEquals(receipt, ShareText.percentDecode(ShareText.percentDecode(receiptEncoded)))
    }

    @Test
    fun bodyFromQueryDecodesTwiceEscapedValue() {
        val twice = receiptEncoded.replace("%", "%25")
        assertEquals(receipt, ShareText.bodyFromQuery("body=$twice"))
    }

    /** A bank app that mapped UTF-8 bytes onto Latin-1 chars. This is the exact
     *  string from issue #304, where it rendered as `ÙØ¨Ù„Øº`. */
    @Test
    fun repairsABodyWhoseBytesWereReadAsLatin1() {
        val mangled = String(receipt.toByteArray(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)
        assertEquals(receipt, ShareText.repairMojibake(mangled))
    }

    @Test
    fun repairRunsAfterDecoding() {
        // "Hello مب" mangled by a Latin-1 sender, then percent-encoded — the
        // two faults stacked, which is what the bank app produces.
        val mangled = String(
            "Hello مب".toByteArray(StandardCharsets.UTF_8),
            StandardCharsets.ISO_8859_1,
        )
        assertEquals(
            "Hello مب",
            ShareText.repairMojibake(ShareText.percentDecode(encode(mangled))),
        )
    }

    @Test
    fun repairLeavesCleanTextAlone() {
        assertEquals(receipt, ShareText.repairMojibake(receipt))
        assertEquals("Hello world", ShareText.repairMojibake("Hello world"))
        assertEquals("", ShareText.repairMojibake(""))
    }

    /** `Café` is Latin-1 but not valid UTF-8, so repairing it would corrupt a
     *  message rather than rescue one. */
    @Test
    fun repairLeavesWesternTextAlone() {
        assertEquals("Café naïve", ShareText.repairMojibake("Café naïve"))
        assertEquals("Grüße", ShareText.repairMojibake("Grüße"))
    }

    @Test
    fun repairLeavesNonLetterLatin1PunctuationAlone() {
        // The em dash decodes cleanly but is not a letter, so there is nothing
        // to rescue: repairing would silently rewrite the message.
        assertEquals("100% — 50", ShareText.repairMojibake("100% — 50"))
    }

    @Test
    fun percentDecodeLeavesPlusAlone() {
        assertEquals("a+b", ShareText.percentDecode("a+b"))
        assertEquals("a b", ShareText.percentDecode("a%20b"))
    }

    @Test
    fun percentDecodeKeepsMalformedEscapesVerbatim() {
        assertEquals("50% off", ShareText.percentDecode("50% off"))
        assertEquals("100%", ShareText.percentDecode("100%"))
        assertEquals("%zz", ShareText.percentDecode("%zz"))
    }

    @Test
    fun percentDecodeIsIdempotentOnUnescapedText() {
        assertEquals(receipt, ShareText.percentDecode(receipt))
    }
}
