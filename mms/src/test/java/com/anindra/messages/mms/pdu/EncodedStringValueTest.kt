package com.anindra.messages.mms.pdu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `Encoded-string-value` is where charset handling goes wrong quietly: a String
 * round-trip through UTF-8 would rewrite every non-ASCII message we send without
 * any test failing, so these assert the bytes themselves.
 */
class EncodedStringValueTest {

    @Test
    fun utf8IsTheDefault() {
        assertEquals(CharacterSets.UTF_8, EncodedStringValue(CharacterSets.UTF_8, "hi").charsetMibEnum)
        assertEquals(CharacterSets.UTF_8, CharacterSets.mibEnum("utf-8"))
        assertEquals(CharacterSets.UTF_8, CharacterSets.mibEnum("charset-we-have-never-heard-of"))
    }

    @Test
    fun textIsDecodedWithTheDeclaredCharsetNotUtf8() {
        val bytes = byteArrayOf(0x82.toByte(), 0xA0.toByte())
        val value = EncodedStringValue(CharacterSets.SHIFT_JIS, bytes)
        assertArrayEquals(bytes, value.textBytes)
        assertEquals("あ", value.text)
    }

    @Test
    fun theWireFormIsCharsetThenTextThenTerminator() {
        val value = EncodedStringValue.utf8("hi")
        val bytes = WspWriter().apply { appendEncodedStringValue(value) }.toByteArray()
        // value-length 4 covers the charset short-integer plus "hi" and its NUL.
        // UTF_8 is 0x6A, carried as short-integer 0x80 or 0x6A = 0xEA.
        assertArrayEquals(byteArrayOf(0x04, 0xEA.toByte(), 0x68, 0x69, 0x00), bytes)
    }

    @Test
    fun aLeadingByteAboveTheQuoteRangeCostsAnExtraOctetAndIsCounted() {
        val value = EncodedStringValue.utf8("é")
        val bytes = WspWriter().apply { appendEncodedStringValue(value) }.toByteArray()
        // value-length, charset short-integer, then the 0x7F quote.
        assertEquals(0x7F, bytes[2].toInt() and 0xFF)
        assertEquals(value.encodedLength(), bytes.size - 1)
    }

    @Test
    fun encodedLengthAgreesWithWhatTheWriterEmitted() {
        for (text in listOf("", "a", "hello world", "é", "🎉")) {
            val value = EncodedStringValue.utf8(text)
            val nested = WspWriter().apply { appendTextStringBytes(value.textBytes) }.toByteArray()
            assertEquals(nested.size, value.encodedLength() - 1)
        }
    }

    @Test
    fun roundTripKeepsTheBytesIdentical() {
        val original = EncodedStringValue(CharacterSets.ISO_8859_1, "café")
        val bytes = WspWriter().apply { appendEncodedStringValue(original) }.toByteArray()
        val decoded = WspReader(bytes).readEncodedStringValue()
        assertEquals(original, decoded)
        assertArrayEquals(original.textBytes, decoded.textBytes)
    }

    @Test
    fun aDeclaredLengthLongerThanTheDataIsRejected() {
        // value-length 40 with nothing following it.
        val reader = WspReader(byteArrayOf(40, 0xAA.toByte(), 0x68))
        try {
            reader.readEncodedStringValue()
            throw AssertionError("expected rejection")
        } catch (expected: MalformedPduException) {
            // Otherwise the reader would decode whatever bytes happened to follow.
        }
    }

    @Test
    fun equalityComparesCharsetAndBytes() {
        assertEquals(EncodedStringValue.utf8("x"), EncodedStringValue.utf8("x"))
        assertEquals(false, EncodedStringValue.utf8("x") == EncodedStringValue.utf8("y"))
        assertEquals(
            false,
            EncodedStringValue.utf8("x") == EncodedStringValue(CharacterSets.UTF_16, "x"),
        )
    }
}
