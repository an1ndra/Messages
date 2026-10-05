package com.anindra.messages.mms.pdu

/**
 * Thrown when a PDU cannot be decoded. `PduParser` catches this and reports a
 * null PDU rather than letting it escape, because the input is whatever a
 * carrier pushed at us.
 */
class MalformedPduException(message: String) : RuntimeException(message)

/**
 * WSP (WAP-230) primitive encoding, the layer MMS PDUs are built on.
 *
 * A writer never back-patches in place: a length-prefixed value is built in a
 * nested writer so the prefix width can be chosen after the length is known.
 * That costs one extra copy of a bounded value and removes the class of bug
 * where a placeholder prefix turns out to be the wrong width.
 */
class WspWriter {
    private val out = java.io.ByteArrayOutputStream(256)

    val size: Int get() = out.size()

    fun appendOctet(value: Int) {
        out.write(value and 0xFF)
    }

    /** 7-bit value carried in the low bits of one byte, high bit set. */
    fun appendShortInteger(value: Int) {
        if (value < 0 || value > 0x7F) {
            throw IllegalArgumentException("short-integer out of range: $value")
        }
        out.write((value or 0x80) and 0xFF)
    }

    /** Only valid below [LENGTH_QUOTE]; wider values need [appendValueLength]. */
    fun appendShortLength(value: Int) {
        if (value < 0 || value > MAX_SHORT_LENGTH) {
            throw IllegalArgumentException("short-length out of range: $value")
        }
        out.write(value)
    }

    fun appendValueLength(value: Int) {
        if (value < 0) throw IllegalArgumentException("value-length out of range: $value")
        if (value < LENGTH_QUOTE) {
            out.write(value)
        } else {
            out.write(LENGTH_QUOTE)
            appendUintvar(value.toLong())
        }
    }

    fun appendUintvar(value: Long) {
        if (value < 0) throw IllegalArgumentException("uintvar out of range: $value")
        var remaining = value
        do {
            var byte = (remaining and 0x7FL).toInt()
            remaining = remaining ushr 7
            if (remaining != 0L) byte = byte or 0x80
            out.write(byte)
        } while (remaining != 0L)
    }

    fun appendLongInteger(value: Long) {
        if (value < 0) throw IllegalArgumentException("long-integer out of range: $value")
        var octets = 1
        var probe = value
        while (probe > 0xFF) {
            probe = probe ushr 8
            octets++
        }
        if (octets > MAX_LONG_INTEGER_OCTETS) {
            throw IllegalArgumentException("long-integer needs $octets octets")
        }
        out.write(octets)
        for (shift in (octets - 1) downTo 0) {
            out.write(((value ushr (shift * 8)) and 0xFF).toInt())
        }
    }

    /** Short-integer when it fits, otherwise a long-integer. */
    fun appendIntegerValue(value: Long) {
        if (value in 0..0x7F) appendShortInteger(value.toInt()) else appendLongInteger(value)
    }

    /** A 0x7F quote is required when the first byte would otherwise read as a token. */
    fun appendTextString(value: String) = appendTextStringBytes(value.toByteArray(Charsets.UTF_8))

    fun appendTextStringBytes(bytes: ByteArray) {
        if (bytes.isNotEmpty() && bytes[0].toInt() and 0xFF > 0x7F) out.write(TEXT_STRING_QUOTE)
        out.write(bytes, 0, bytes.size)
        out.write(0x00)
    }

    fun appendQuotedString(value: String) {
        out.write(QUOTED_STRING_START)
        out.write(value.toByteArray(Charsets.UTF_8))
        out.write(0x00)
    }

    fun appendEncodedStringValue(value: EncodedStringValue) {
        appendValueLength(value.encodedLength())
        appendShortInteger(value.charsetMibEnum)
        appendTextStringBytes(value.textBytes)
    }

    fun appendConstrainedMedia(contentType: String) {
        val index = ContentTypes.indexOf(contentType)
        if (index == null) appendTextString(contentType) else appendShortInteger(index)
    }

    fun appendBytes(bytes: ByteArray) = out.write(bytes, 0, bytes.size)

    fun appendRawByte(value: Int) = out.write(value and 0xFF)

    /** Builds `value-length(value())` in one step. */
    fun <T> appendValueLengthPrefixed(block: WspWriter.() -> T): T {
        val nested = WspWriter()
        val result = nested.block()
        val bytes = nested.toByteArray()
        appendValueLength(bytes.size)
        appendBytes(bytes)
        return result
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    companion object {
        const val LENGTH_QUOTE = 0x1F
        const val MAX_SHORT_LENGTH = 30
        const val MAX_LONG_INTEGER_OCTETS = 8
        const val TEXT_STRING_QUOTE = 0x7F
        const val QUOTED_STRING_START = 0x22
    }
}

/**
 * Cursor over a PDU's bytes. Every read is bounds-checked and throws
 * [MalformedPduException] rather than reading past the end, and nothing is
 * allocated from a length that has not been checked against [remaining].
 */
class WspReader(private val data: ByteArray, start: Int = 0, private val end: Int = data.size) {
    var index: Int = start
        private set

    val remaining: Int get() = end - index
    val exhausted: Boolean get() = index >= end

    fun mark(): Int = index

    fun reset(to: Int) {
        index = to
    }

    fun seek(to: Int) {
        require(to in 0..end) { "seek out of range: $to" }
        index = to
    }

    fun peekOctet(): Int {
        if (exhausted) throw MalformedPduException("peek past end at $index")
        return data[index].toInt() and 0xFF
    }

    fun readOctet(): Int {
        if (exhausted) throw MalformedPduException("octet past end at $index")
        return data[index++].toInt() and 0xFF
    }

    /**
     * Tolerant of a missing high bit: some carriers emit short-integers without
     * it, and rejecting those would drop messages that other clients accept.
     */
    fun readShortInteger(): Int = readOctet() and 0x7F

    fun readShortLength(): Int {
        val value = readOctet()
        if (value > MAX_SHORT_LENGTH) throw MalformedPduException("short-length $value > $MAX_SHORT_LENGTH")
        return value
    }

    fun readValueLength(): Int {
        val first = readOctet()
        if (first < LENGTH_QUOTE) return first
        if (first != LENGTH_QUOTE) throw MalformedPduException("value-length quote $first invalid")
        val value = readUintvar()
        if (value > Int.MAX_VALUE) throw MalformedPduException("value-length $value out of range")
        return value.toInt()
    }

    fun readUintvar(): Long {
        var result = 0L
        var shift = 0
        var count = 0
        while (true) {
            if (count == 5) throw MalformedPduException("uintvar longer than 5 octets")
            val byte = readOctet()
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            count++
        }
    }

    fun readLongInteger(): Long {
        val octets = readShortLength()
        if (octets > MAX_LONG_INTEGER_OCTETS) throw MalformedPduException("long-integer $octets octets")
        var value = 0L
        repeat(octets) { value = (value shl 8) or readOctet().toLong() }
        return value
    }

    fun readIntegerValue(): Long =
        if (peekOctet() > 0x7F) readShortInteger().toLong() else readLongInteger()

    fun readTextString(): String = String(readTextStringBytes(), Charsets.UTF_8)

    fun readQuotedString(): String = String(readWapStringBytes(QUOTED_STRING_START), Charsets.UTF_8)

    fun readTextStringBytes(): ByteArray = readWapStringBytes(TEXT_STRING_QUOTE)

    private fun readWapStringBytes(quote: Int): ByteArray {
        if (exhausted) throw MalformedPduException("string past end at $index")
        if (data[index].toInt() and 0xFF == quote) index++
        val start = index
        while (index < end) {
            if (data[index].toInt() == 0x00) {
                val slice = data.copyOfRange(start, index)
                index++
                return slice
            }
            index++
        }
        throw MalformedPduException("unterminated string from $start")
    }

    fun readEncodedStringValue(): EncodedStringValue {
        val length = readValueLength()
        val stop = index + length
        if (length < 0 || stop > end) {
            throw MalformedPduException("encoded-string-value length $length overruns $remaining")
        }
        val charset = readShortInteger()
        val textBytes = readTextStringBytes()
        index = stop
        return EncodedStringValue(charset, textBytes)
    }

    /**
     * An index past the end of the constrained-media table means the sender used
     * a code this version does not know; [ContentTypes.WILDCARD] is the
     * spec-defined fallback.
     */
    fun readConstrainedMedia(): String {
        val first = peekOctet()
        return if (first > TEXT_STRING_QUOTE) {
            ContentTypes.at(readShortInteger())
        } else {
            readTextString()
        }
    }

    fun readBytes(count: Int): ByteArray {
        if (count < 0 || count > remaining) throw MalformedPduException("read $count bytes, $remaining left")
        val slice = data.copyOfRange(index, index + count)
        index += count
        return slice
    }

    fun skip(count: Int) {
        if (count < 0 || count > remaining) throw MalformedPduException("skip $count bytes, $remaining left")
        index += count
    }

    /**
     * Skips one WSP value of unknown shape: a text-string when the leading octet
     * could begin one, otherwise a single short-integer octet. Returns -1 when
     * the data ends first, which is the caller's signal to abandon the
     * structure rather than guess at a length.
     */
    fun skipWapValue(): Int {
        if (exhausted) return -1
        return try {
            val first = peekOctet()
            if (first > TEXT_STRING_QUOTE) {
                skip(1)
                1
            } else {
                readTextStringBytes()
                1
            }
        } catch (_: MalformedPduException) {
            -1
        }
    }

    companion object {
        const val LENGTH_QUOTE = 0x1F
        const val MAX_SHORT_LENGTH = 30
        const val MAX_LONG_INTEGER_OCTETS = 8
        const val TEXT_STRING_QUOTE = 0x7F
        const val QUOTED_STRING_START = 0x22
    }
}
