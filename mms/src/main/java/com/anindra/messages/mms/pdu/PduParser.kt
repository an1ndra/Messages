package com.anindra.messages.mms.pdu

import java.util.Base64

/**
 * Decodes the octets a carrier pushed at us into a [Pdu], per
 * `docs/Mms/02-pdu-wire-format.md`.
 *
 * The contract is that [parse] never throws, never loops and never allocates
 * from a length it has not checked against the bytes actually present. The
 * input is hostile by default, so every rejection is a `null` and nothing else.
 */
class PduParser(
    private val data: ByteArray,
    private val parseContentDisposition: Boolean = true,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    private var alternativeNesting = 0

    /** A content-type value with the parameters this layer keeps. */
    private class MediaType(
        val type: String,
        val start: String? = null,
        val name: String? = null,
        val charset: Int? = null,
    )

    fun parse(): Pdu? = try {
        parseChecked()
    } catch (_: MalformedPduException) {
        null
    } catch (_: IndexOutOfBoundsException) {
        // The contract is a null on hostile input, never a throw. A reader
        // that somehow reads past the buffer must still not surface as a
        // crash in the face of whatever a carrier pushed at us.
        null
    }

    private fun parseChecked(): Pdu {
        val reader = WspReader(data)
        val pdu = Pdu()
        val startContentId = parseHeaders(reader, pdu)

        val messageType = pdu.headers.octetOrNull(HeaderField.MESSAGE_TYPE)
            ?: throw MalformedPduException("no X-Mms-Message-Type")
        if (!MessageType.isSupported(messageType)) {
            throw MalformedPduException("unsupported message type 0x%02X".format(messageType))
        }
        pdu.messageType = messageType
        pdu.headers.octetOrNull(HeaderField.MMS_VERSION)?.let { pdu.mmsVersion = it }
        pdu.headers.checkMandatory(messageType)

        val contentType = pdu.contentType?.let { ContentTypes.normalize(it) }
        val wantsBody = when (messageType) {
            MessageType.SEND_REQ -> true
            MessageType.RETRIEVE_CONF -> {
                if (contentType !in RETRIEVE_CONF_TYPES) {
                    throw MalformedPduException("retrieve-conf content type $contentType")
                }
                // Some carriers omit Retrieve-Status entirely, so absent means OK.
                pdu.retrieveStatus == null || pdu.retrieveStatus == HeaderField.RETRIEVE_STATUS_OK
            }
            else -> false
        }
        if (wantsBody) {
            if (contentType == null || !ContentTypes.isMultipart(contentType)) {
                throw MalformedPduException("body under non-multipart content type $contentType")
            }
            pdu.body = parseBody(reader, startContentId)
        }
        return pdu
    }

    /**
     * Reads the header block and returns the multipart container's `start`
     * parameter, which decides which part leads the body.
     *
     * The block ends at the first octet below [HeaderField.FIRST]: that is the
     * body's uintvar entry-count, one octet for any plausible part count. Octets
     * at or above it stay in the block even when this version declares no field
     * for them, so an unknown field is skipped rather than mistaken for the body.
     */
    private fun parseHeaders(reader: WspReader, pdu: Pdu): String? {
        var startContentId: String? = null
        while (!reader.exhausted) {
            val field = reader.peekOctet()
            if (field < HeaderField.FIRST) break
            reader.readOctet()
            startContentId = parseField(reader, pdu.headers, field) ?: startContentId
        }
        return startContentId
    }

    /** Stores one header; returns the `start` parameter when the field is Content-Type. */
    private fun parseField(reader: WspReader, headers: PduHeaders, field: Int): String? {
        var start: String? = null
        when (HeaderField.kindOf(field)) {
            HeaderField.Kind.OCTET -> {
                // §4: MMS-Version is a short-integer even though the field code
                // sits in the octet block.
                if (field == HeaderField.MMS_VERSION) {
                    headers.setOctet(field, reader.readShortInteger())
                } else {
                    headers.setOctet(field, reader.readOctet())
                }
            }

            HeaderField.Kind.LONG_INTEGER -> {
                val value = if (field == HeaderField.EXPIRY || field == HeaderField.DELIVERY_TIME) {
                    readExpiry(reader)
                } else {
                    reader.readLongInteger()
                }
                headers.setLong(field, value)
            }

            HeaderField.Kind.INTEGER_VALUE -> headers.setLong(field, reader.readIntegerValue())

            HeaderField.Kind.TEXT_STRING -> headers.setText(field, reader.readTextString())

            HeaderField.Kind.QUOTED_STRING -> headers.setQuoted(field, reader.readQuotedString())

            HeaderField.Kind.ENCODED_STRING_VALUE ->
                headers.setEncoded(field, reader.readEncodedStringValue())

            HeaderField.Kind.ENCODED_STRING_VALUE_LIST ->
                headers.addEncoded(field, reader.readEncodedStringValue())

            HeaderField.Kind.FROM -> headers.setFrom(readFrom(reader))

            HeaderField.Kind.MESSAGE_CLASS ->
                // §4: an octet when the leading octet is 0x80 or above, else
                // token text, which a text-string would have quoted.
                if (reader.peekOctet() >= HeaderField.MESSAGE_CLASS_PERSONAL) {
                    headers.setMessageClassOctet(reader.readOctet())
                } else {
                    headers.setMessageClassText(reader.readTextString())
                }

            HeaderField.Kind.CONTENT_TYPE -> {
                val mediaType = readContentType(reader)
                headers.setContentType(mediaType.type)
                start = mediaType.start
            }

            // A field code this version does not declare: skip whatever value
            // shape it carries and keep going.
            null -> if (reader.skipWapValue() < 0) {
                throw MalformedPduException("unskippable header 0x%02X".format(field))
            }
        }
        return start
    }

    /**
     * §4: `<value-length> <absolute|relative-token> <long-integer>`. A relative
     * value is seconds from now, so it is resolved here; left as a delta it
     * would mean nothing to anything downstream.
     */
    private fun readExpiry(reader: WspReader): Long {
        val length = reader.readValueLength()
        if (length < 2 || length > reader.remaining) {
            throw MalformedPduException("expiry value-length $length, ${reader.remaining} left")
        }
        val stop = reader.index + length
        val token = reader.readOctet()
        val seconds = reader.readLongInteger()
        reader.seek(stop)
        return if (token == HeaderField.VALUE_RELATIVE_TOKEN) nowSeconds() + seconds else seconds
    }

    private fun readFrom(reader: WspReader): EncodedStringValue {
        val length = reader.readValueLength()
        if (length < 1 || length > reader.remaining) {
            throw MalformedPduException("From value-length $length, ${reader.remaining} left")
        }
        val token = reader.readOctet()
        return if (token == EncodedStringValue.ADDRESS_PRESENT_TOKEN) {
            reader.readEncodedStringValue()
        } else {
            EncodedStringValue.insertAddressToken()
        }
    }

    private fun readContentType(reader: WspReader): MediaType {
        // Content-type-value = Constrained-media | Content-general-form, and
        // Content-general-form = Value-length Media-type. A first octet below
        // 0x20 is the length; anything else is already the media — a bare
        // short-integer or text-string with no parameters to read. The
        // reference accepts both forms.
        if (reader.peekOctet() >= 0x20) {
            return MediaType(reader.readConstrainedMedia())
        }
        val length = reader.readValueLength()
        if (length < 1 || length > reader.remaining) {
            throw MalformedPduException("content-type value-length $length, ${reader.remaining} left")
        }
        val stop = reader.index + length
        val type = reader.readConstrainedMedia()
        var start: String? = null
        var name: String? = null
        var charset: Int? = null
        while (reader.index < stop) {
            when (reader.readOctet()) {
                PARAM_START -> start = reader.readTextString()
                PduPart.NAME -> name = reader.readTextString()
                PduPart.CHARSET -> charset = reader.readShortInteger()
                else -> if (reader.skipWapValue() < 0) {
                    throw MalformedPduException("unskippable content-type parameter")
                }
            }
        }
        reader.seek(stop)
        return MediaType(type, start, name, charset)
    }

    private fun parseBody(reader: WspReader, startContentId: String? = null): PduBody {
        val count = reader.readUintvar()
        // Every entry spends at least two octets on its two lengths, so a count
        // that cannot fit in what is left is a forged length, not a message.
        if (count * 2 > reader.remaining.toLong()) {
            throw MalformedPduException("entry-count $count, ${reader.remaining} left")
        }
        val body = PduBody()
        repeat(count.toInt()) {
            body.add(parsePart(reader))
        }
        return if (startContentId == null) body else startFirst(body, startContentId)
    }

    /** §7: the `start` parameter names the part that leads the body. */
    private fun startFirst(body: PduBody, startContentId: String): PduBody {
        val start = startContentId.trim('<', '>')
        val parts = body.parts()
        val index = parts.indexOfFirst { it.contentIdOrNull() == start }
        if (index <= 0) return body
        body.removeAll()
        body.add(parts[index])
        parts.forEachIndexed { position, part -> if (position != index) body.add(part) }
        return body
    }

    private fun parsePart(reader: WspReader): PduPart {
        val headerLength = reader.readUintvar()
        val dataLength = reader.readUintvar()
        if (headerLength > reader.remaining.toLong()) {
            throw MalformedPduException("part header-length $headerLength, ${reader.remaining} left")
        }

        val headerStart = reader.index
        val mediaType = readContentType(reader)
        // A part whose declared header-length is shorter than the content-type
        // it opens with is forged, and reading on would walk into the data.
        var remainingHeaderBytes = headerLength.toInt() - (reader.index - headerStart)
        if (remainingHeaderBytes < 0) throw MalformedPduException("content-type overruns header-length")

        val part = PduPart()
        part.contentType = mediaType.type
        part.name = mediaType.name
        part.charset = mediaType.charset
        while (remainingHeaderBytes > 0) {
            val fieldStart = reader.index
            when (val field = reader.readOctet()) {
                PduPart.CONTENT_ID -> part.contentId = reader.readQuotedString()
                PduPart.CONTENT_LOCATION -> part.contentLocation = reader.readTextString()
                PART_CONTENT_DISPOSITION -> readContentDisposition(reader, part)
                PART_CONTENT_TRANSFER_ENCODING -> part.transferEncoding = reader.readTextString()
                else -> if (reader.skipWapValue() < 0) {
                    throw MalformedPduException(
                        "unskippable part header 0x%02X".format(field),
                    )
                }
            }
            remainingHeaderBytes -= reader.index - fieldStart
        }
        // §7: carriers do send nameless parts, and one that cannot be re-composed
        // cannot be forwarded.
        if (part.name.isNullOrBlank() &&
            part.contentLocation.isNullOrBlank() &&
            part.contentId.isNullOrBlank()
        ) {
            part.name = part.effectiveName()
        }

        // Checked against what is actually left before a single byte is copied:
        // this is the line a hostile push must not get past.
        if (dataLength > reader.remaining.toLong()) {
            throw MalformedPduException("part data-length $dataLength, ${reader.remaining} left")
        }
        val raw = reader.readBytes(dataLength.toInt())

        if (part.contentType?.let(ContentTypes::normalize) in ALTERNATIVE_TYPES) {
            // §7: an alternative wrapper is flattened to its first child. Each
            // level recurses, so the depth is capped rather than left to the
            // size of whatever a hostile push chose to send.
            if (alternativeNesting >= MAX_ALTERNATIVE_NESTING) {
                throw MalformedPduException("alternatives nested past $MAX_ALTERNATIVE_NESTING")
            }
            alternativeNesting++
            try {
                return parseBody(WspReader(raw)).partAt(0)
                    ?: throw MalformedPduException("multipart/alternative with no parts")
            } finally {
                alternativeNesting--
            }
        }
        part.data = decodeTransferEncoding(part.transferEncoding, raw)
            ?: throw MalformedPduException("part data does not decode")
        return part
    }

    private fun readContentDisposition(reader: WspReader, part: PduPart) {
        val length = reader.readValueLength()
        if (length < 1 || length > reader.remaining) {
            throw MalformedPduException("content-disposition value-length $length, ${reader.remaining} left")
        }
        if (!parseContentDisposition) {
            // Some MMSC servers emit this field wrong, so its octets are
            // stepped over rather than guessed at.
            reader.skip(length)
            return
        }
        val stop = reader.index + length
        part.contentDisposition = if (reader.peekOctet() >= DISPOSITION_FORM_DATA) {
            DISPOSITION_NAMES[reader.readOctet()]
        } else {
            reader.readTextString()
        }
        reader.seek(stop)
    }

    private fun decodeTransferEncoding(encoding: String?, bytes: ByteArray): ByteArray? =
        when (encoding?.trim()?.lowercase()) {
            null, "binary" -> bytes
            PduPart.BASE64 -> runCatching { Base64.getMimeDecoder().decode(bytes) }.getOrNull()
            PduPart.QUOTED_PRINTABLE -> decodeQuotedPrintable(bytes)
            else -> bytes
        }

    private fun decodeQuotedPrintable(bytes: ByteArray): ByteArray? {
        val out = java.io.ByteArrayOutputStream(bytes.size)
        var index = 0
        while (index < bytes.size) {
            val octet = bytes[index].toInt() and 0xFF
            if (octet != EQUALS) {
                out.write(octet)
                index++
                continue
            }
            if (index + 1 >= bytes.size) return null
            val next = bytes[index + 1].toInt() and 0xFF
            when (next) {
                '\n'.code -> index += 2
                '\r'.code -> {
                    index += 2
                    if (index < bytes.size && bytes[index].toInt() == '\n'.code) index++
                }
                else -> {
                    if (index + 2 >= bytes.size) return null
                    val high = hexDigit(next) ?: return null
                    val low = hexDigit(bytes[index + 2].toInt() and 0xFF) ?: return null
                    out.write((high shl 4) or low)
                    index += 3
                }
            }
        }
        return out.toByteArray()
    }

    private fun hexDigit(octet: Int): Int? = when (octet) {
        in '0'.code..'9'.code -> octet - '0'.code
        in 'A'.code..'F'.code -> octet - 'A'.code + 10
        in 'a'.code..'f'.code -> octet - 'a'.code + 10
        else -> null
    }

    private companion object {
        /** §6 content-type parameter naming the leading part. */
        const val PARAM_START = 0x8A

        const val PART_CONTENT_DISPOSITION = 0xC5
        const val PART_CONTENT_TRANSFER_ENCODING = 0xC8

        const val DISPOSITION_FORM_DATA = 0x80
        const val EQUALS = '='.code

        /** A real message nests one alternative deep; more is a forged body. */
        const val MAX_ALTERNATIVE_NESTING = 8

        val DISPOSITION_NAMES = mapOf(
            DISPOSITION_FORM_DATA to "form-data",
            0x81 to "attachment",
            0x82 to "inline",
        )

        /** A container part whose alternatives the client picks between. */
        val ALTERNATIVE_TYPES = setOf(
            ContentTypes.MULTIPART_ALTERNATIVE,
            "application/vnd.wap.multipart.alternative",
        )

        /** The containers an M-Retrieve.conf body is allowed to use. */
        val RETRIEVE_CONF_TYPES = setOf(
            ContentTypes.MULTIPART_MIXED,
            ContentTypes.MULTIPART_ALTERNATIVE,
            ContentTypes.MULTIPART_RELATED,
            "multipart/related",
        )
    }
}