package com.anindra.messages.mms.pdu

/**
 * Serialises a [Pdu] into the octets an MMSC expects, per
 * `docs/Mms/02-pdu-wire-format.md`.
 *
 * X-Mms-Content-Type closes the header block — receivers, including the
 * production reference, stop reading headers at it — so it is emitted last
 * whatever its field code sorts as. The rest go out in ascending field-code
 * order, which makes two identical PDUs produce identical octets.
 *
 * Composition fails by returning null rather than throwing. Every reason is a
 * property of the PDU being handed over — a missing mandatory header, an
 * unencodable value — and the caller cannot act on an exception any better
 * than on a null.
 */
object PduComposer {

    /** §3: the only types this client ever sends. */
    private val COMPOSABLE_TYPES = setOf(
        MessageType.SEND_REQ,
        MessageType.NOTIFYRESP_IND,
        MessageType.ACKNOWLEDGE_IND,
        MessageType.READ_REC_IND,
    )

    /** §6: the two content-type parameters on the multipart container header. */
    private const val PARAM_START = 0x8A
    private const val PARAM_TYPE = 0x89

    fun compose(pdu: Pdu): ByteArray? = try {
        composeWithinRange(pdu)
    } catch (_: IllegalArgumentException) {
        // A WSP primitive refused the value, so the PDU cannot be encoded at all.
        null
    } catch (_: MalformedPduException) {
        // checkMandatory rejecting the header set is a composition failure too.
        null
    }

    private fun composeWithinRange(pdu: Pdu): ByteArray? {
        if (pdu.messageType !in COMPOSABLE_TYPES) return null
        // checkMandatory reads MMS-Version out of the header store while a caller
        // declares it as a Pdu field, so the check runs against a store holding
        // both instead of against the caller's own PDU.
        val mandatory = PduHeaders().also { it.copyFrom(pdu.headers) }
        if (!mandatory.has(HeaderField.MMS_VERSION)) {
            mandatory.setOctet(HeaderField.MMS_VERSION, pdu.mmsVersion)
        }
        mandatory.checkMandatory(pdu.messageType)
        // §3 adds a recipient requirement the per-type mandatory list cannot
        // express, because any one of the three recipient fields satisfies it.
        if (pdu.messageType == MessageType.SEND_REQ &&
            pdu.to.isEmpty() && pdu.cc.isEmpty() && pdu.bcc.isEmpty()
        ) {
            return null
        }

        val out = WspWriter()
        val fields = sortedSetOf(HeaderField.MESSAGE_TYPE, HeaderField.MMS_VERSION)
        fields.addAll(pdu.headers.fieldCodes)
        // X-Mms-Content-Type closes the header block: receivers stop reading
        // headers at it, so it is emitted last whatever its field code sorts as.
        fields.remove(HeaderField.CONTENT_TYPE)
        for (field in fields) {
            out.appendBytes(encodeField(pdu, field) ?: return null)
        }
        pdu.headers.contentTypeOrNull()?.let {
            out.appendBytes(encodeContentType(pdu) ?: return null)
        }
        if (MessageType.hasBody(pdu.messageType)) {
            out.appendBytes(encodeBody(pdu) ?: return null)
        }
        return out.toByteArray()
    }

    private fun encodeField(pdu: Pdu, field: Int): ByteArray? {
        val out = WspWriter()
        return when (field) {
            HeaderField.MESSAGE_TYPE -> {
                out.appendOctet(HeaderField.MESSAGE_TYPE)
                out.appendOctet(pdu.messageType)
                out.toByteArray()
            }

            HeaderField.MMS_VERSION -> {
                val version = pdu.headers.octetOrNull(HeaderField.MMS_VERSION) ?: pdu.mmsVersion
                out.appendOctet(HeaderField.MMS_VERSION)
                // §4: MMS-Version is a short-integer, so 1.2 goes out as 0x12.
                out.appendShortInteger(version)
                out.toByteArray()
            }

            else -> encodeByKind(pdu, field)
        }
    }

    private fun encodeByKind(pdu: Pdu, field: Int): ByteArray? {
        val headers = pdu.headers
        val out = WspWriter()
        return when (HeaderField.kindOf(field)) {
            HeaderField.Kind.OCTET -> {
                out.appendOctet(field)
                out.appendOctet(headers.octetOrNull(field) ?: return null)
                out.toByteArray()
            }

            HeaderField.Kind.LONG_INTEGER -> {
                val seconds = headers.longOrNull(field) ?: return null
                out.appendOctet(field)
                if (field == HeaderField.EXPIRY || field == HeaderField.DELIVERY_TIME) {
                    // §4: <value-length> <0x81 relative-token> <long-integer seconds>
                    out.appendValueLengthPrefixed {
                        appendOctet(HeaderField.VALUE_RELATIVE_TOKEN)
                        appendLongInteger(seconds)
                    }
                } else {
                    out.appendLongInteger(seconds)
                }
                out.toByteArray()
            }

            HeaderField.Kind.TEXT_STRING -> {
                out.appendOctet(field)
                out.appendTextString(headers.textOrNull(field) ?: return null)
                out.toByteArray()
            }

            HeaderField.Kind.QUOTED_STRING -> {
                out.appendOctet(field)
                out.appendQuotedString(headers.quotedOrNull(field) ?: return null)
                out.toByteArray()
            }

            HeaderField.Kind.ENCODED_STRING_VALUE -> {
                out.appendOctet(field)
                out.appendEncodedStringValue(headers.encodedOrNull(field) ?: return null)
                out.toByteArray()
            }

            HeaderField.Kind.ENCODED_STRING_VALUE_LIST -> {
                val values = headers.encodedList(field)
                if (values.isEmpty()) return null
                for (value in values) {
                    // §4: one field code per value, so a group message repeats
                    // 0x97 once per recipient rather than packing them together.
                    out.appendOctet(field)
                    out.appendEncodedStringValue(value)
                }
                out.toByteArray()
            }

            HeaderField.Kind.FROM -> encodeFrom(pdu)

            HeaderField.Kind.MESSAGE_CLASS -> {
                out.appendOctet(HeaderField.MESSAGE_CLASS)
                val octet = headers.messageClassOctetOrNull()
                if (octet != null) {
                    out.appendOctet(octet)
                } else {
                    out.appendTextString(headers.messageClassTextOrNull() ?: return null)
                }
                out.toByteArray()
            }

            HeaderField.Kind.CONTENT_TYPE -> encodeContentType(pdu)

            // No store can hold an integer-value, so nothing can be set here.
            HeaderField.Kind.INTEGER_VALUE, null -> null
        }
    }

    private fun encodeFrom(pdu: Pdu): ByteArray {
        val out = WspWriter()
        out.appendOctet(HeaderField.FROM)
        // 0x81 is unassigned as a charset, which is what lets it double as the
        // insert-address marker on a From with no address.
        val address = pdu.from?.takeIf {
            it.charsetMibEnum != EncodedStringValue.INSERT_ADDRESS_TOKEN && it.text.isNotEmpty()
        }
        if (address == null) {
            out.appendValueLength(1)
            out.appendOctet(EncodedStringValue.INSERT_ADDRESS_TOKEN)
        } else {
            out.appendValueLengthPrefixed {
                appendOctet(EncodedStringValue.ADDRESS_PRESENT_TOKEN)
                appendEncodedStringValue(address)
            }
        }
        return out.toByteArray()
    }

    private fun encodeContentType(pdu: Pdu): ByteArray? {
        val contentType = pdu.headers.contentTypeOrNull() ?: return null
        val first = pdu.body?.partAt(0)
        val out = WspWriter()
        out.appendOctet(HeaderField.CONTENT_TYPE)
        out.appendValueLengthPrefixed {
            appendConstrainedMedia(contentType)
            // §6 orders these start-then-type, which is also the order the
            // production reference emits and the one carriers accept.
            first?.contentIdOrNull()?.let {
                appendOctet(PARAM_START)
                appendTextString("<$it>")
            }
            first?.contentType?.let {
                appendOctet(PARAM_TYPE)
                appendTextString(it)
            }
        }
        return out.toByteArray()
    }

    private fun encodeBody(pdu: Pdu): ByteArray? {
        val body = pdu.body ?: return null
        val contentType = pdu.headers.contentTypeOrNull() ?: return null
        // §6: the content type of the message as a whole is a multipart code.
        if (!ContentTypes.isMultipart(contentType)) return null
        val parts = body.parts()
        val out = WspWriter()
        out.appendUintvar(parts.size.toLong())
        for (part in parts) {
            out.appendBytes(encodePart(part) ?: return null)
        }
        return out.toByteArray()
    }

    private fun encodePart(part: PduPart): ByteArray? {
        val contentType = part.contentType ?: return null
        // §6: name, filename and content-location are interchangeable, and a
        // part with none of them cannot be re-composed, which is what breaks
        // forwarding.
        val name = part.name?.takeIf { it.isNotBlank() }
            ?: part.contentLocation?.takeIf { it.isNotBlank() }
            ?: return null

        val headers = WspWriter().apply {
            appendValueLengthPrefixed {
                appendConstrainedMedia(contentType)
                appendOctet(PduPart.NAME)
                appendTextString(name)
                part.charset?.let {
                    appendOctet(PduPart.CHARSET)
                    appendShortInteger(it)
                }
            }
            part.contentIdOrNull()?.let {
                appendOctet(PduPart.CONTENT_ID)
                appendQuotedString("<$it>")
            }
            part.contentLocation?.takeIf { it.isNotBlank() }?.let {
                appendOctet(PduPart.CONTENT_LOCATION)
                appendTextString(it)
            }
        }
        return frame(headers.toByteArray(), part.data ?: ByteArray(0))
    }

    /**
     * Prefixes a part with its two lengths and reads them back, so a length that
     * does not describe the octets beside it fails composition here rather than
     * at the MMSC. The production reference throws at the same point.
     */
    private fun frame(headerBytes: ByteArray, dataBytes: ByteArray): ByteArray? {
        val framing = WspWriter().apply {
            appendUintvar(headerBytes.size.toLong())
            appendUintvar(dataBytes.size.toLong())
        }
        val check = WspReader(framing.toByteArray())
        if (check.readUintvar() != headerBytes.size.toLong()) return null
        if (check.readUintvar() != dataBytes.size.toLong()) return null
        if (!check.exhausted) return null
        return WspWriter().apply {
            appendBytes(framing.toByteArray())
            appendBytes(headerBytes)
            appendBytes(dataBytes)
        }.toByteArray()
    }
}