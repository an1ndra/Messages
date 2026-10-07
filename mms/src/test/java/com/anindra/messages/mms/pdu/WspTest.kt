package com.anindra.messages.mms.pdu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The WSP primitive layer is the one place where a wrong octet produces a PDU
 * that still parses and is then rejected by the carrier, so these tests pin the
 * encodings rather than just round-tripping them.
 */
class WspTest {

    private fun hex(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun shortIntegerSetsTheHighBitAndKeepsSevenBits() {
        val writer = WspWriter()
        writer.appendShortInteger(0x00)
        writer.appendShortInteger(0x7F)
        assertArrayEquals(hex(0x80, 0xFF), writer.toByteArray())

        val reader = WspReader(writer.toByteArray())
        assertEquals(0x00, reader.readShortInteger())
        assertEquals(0x7F, reader.readShortInteger())
    }

    @Test
    fun shortIntegerRejectsAValueThatWouldNotFit() {
        val writer = WspWriter()
        try {
            writer.appendShortInteger(0x80)
            throw AssertionError("expected rejection")
        } catch (expected: IllegalArgumentException) {
            // A truncated value here would silently corrupt every following field.
        }
    }

    @Test
    fun valueLengthIsOneOctetBelowTheQuoteAndUintvarAtOrAbove() {
        val below = WspWriter().apply { appendValueLength(30) }.toByteArray()
        assertArrayEquals(hex(30), below)

        val at = WspWriter().apply { appendValueLength(31) }.toByteArray()
        assertArrayEquals(hex(0x1F, 0x1F), at)

        val above = WspWriter().apply { appendValueLength(300) }.toByteArray()
        assertArrayEquals(hex(0x1F, 0x82, 0x2C), above)
    }

    @Test
    fun uintvarUsesTheMinimumNumberOfOctets() {
        assertArrayEquals(hex(0x00), WspWriter().apply { appendUintvar(0) }.toByteArray())
        assertArrayEquals(hex(0x7F), WspWriter().apply { appendUintvar(127) }.toByteArray())
        // WAP-230 §3.1: 128 is 0x81 0x00 — most significant group first.
        assertArrayEquals(hex(0x81, 0x00), WspWriter().apply { appendUintvar(128) }.toByteArray())
    }

    @Test
    fun uintvarLongerThanFiveOctetsIsRejectedRatherThanLooping() {
        val reader = WspReader(hex(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF))
        try {
            reader.readUintvar()
            throw AssertionError("expected rejection")
        } catch (expected: MalformedPduException) {
            // Six continuation bytes would otherwise spin forever on hostile input.
        }
    }

    @Test
    fun longIntegerIsMinimalAndRoundTrips() {
        for (value in listOf(0L, 1L, 255L, 256L, 65535L, 1L shl 40)) {
            val bytes = WspWriter().apply { appendLongInteger(value) }.toByteArray()
            assertEquals(value, WspReader(bytes).readLongInteger())
        }
    }

    @Test
    fun integerValuePicksTheNarrowerOfTheTwoForms() {
        val small = WspWriter().apply { appendIntegerValue(0x7F) }.toByteArray()
        assertArrayEquals(hex(0xFF), small)

        val large = WspWriter().apply { appendIntegerValue(0x80) }.toByteArray()
        assertArrayEquals(hex(0x01, 0x80), large)

        assertEquals(0x7FL, WspReader(small).readIntegerValue())
        assertEquals(0x80L, WspReader(large).readIntegerValue())
    }

    @Test
    fun textStringQuotesALeadingByteThatWouldOtherwiseReadAsAToken() {
        val plain = WspWriter().apply { appendTextString("hi") }.toByteArray()
        assertArrayEquals(hex(0x68, 0x69, 0x00), plain)

        val quoted = WspWriter().apply { appendTextString("é") }.toByteArray()
        assertEquals(0x7F, quoted[0].toInt() and 0xFF)
        assertEquals("é", WspReader(quoted).readTextString())
    }

    @Test
    fun textStringSurvivesNonAscii() {
        val original = "नमस्ते 🎉"
        val bytes = WspWriter().apply { appendTextString(original) }.toByteArray()
        assertEquals(original, WspReader(bytes).readTextString())
    }

    @Test
    fun unterminatedStringIsRejected() {
        val reader = WspReader(hex(0x61, 0x62))
        try {
            reader.readTextString()
            throw AssertionError("expected rejection")
        } catch (expected: MalformedPduException) {
            // Runs off the end rather than returning a truncated string.
        }
    }

    @Test
    fun readsPastTheEndThrowsInsteadOfReturningZero() {
        val reader = WspReader(ByteArray(0))
        assertTrue(reader.exhausted)
        try {
            reader.readOctet()
            throw AssertionError("expected rejection")
        } catch (expected: MalformedPduException) {
            // A silent zero here would read as message-type 0.
        }
    }

    @Test
    fun readBytesRefusesMoreThanRemains() {
        val reader = WspReader(hex(0x01, 0x02))
        try {
            reader.readBytes(3)
            throw AssertionError("expected rejection")
        } catch (expected: MalformedPduException) {
            // This is the check that stops a forged part length becoming an allocation.
        }
    }

    @Test
    fun valueLengthPrefixedBlockNestsCorrectly() {
        val writer = WspWriter()
        writer.appendOctet(0x8C)
        writer.appendValueLengthPrefixed {
            // Message-Type is an octet; MMS-Version is a short-integer.
            appendOctet(MessageType.NOTIFICATION_IND)
            appendShortInteger(HeaderField.MMS_VERSION_1_2)
        }
        assertArrayEquals(hex(0x8C, 0x02, 0x82, 0x92), writer.toByteArray())
    }

    @Test
    fun skipWapValueReportsFailureInsteadOfGuessing() {
        assertEquals(-1, WspReader(ByteArray(0)).skipWapValue())
        assertEquals(-1, WspReader(hex(0x61, 0x62)).skipWapValue())
    }
}
