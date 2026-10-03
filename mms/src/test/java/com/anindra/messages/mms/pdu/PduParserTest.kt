package com.anindra.messages.mms.pdu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The parser's contract is stated by the hostile-input table in §7 of
 * `docs/Mms/02-pdu-wire-format.md`: return null, never throw, never loop, never
 * allocate from an unvalidated length. The fixtures here are hand-built octets
 * rather than composed PDUs, because a composer that is wrong in the same way
 * as the parser would make every test pass.
 */
class PduParserTest {

    private val now = 1_700_000_000L

    private fun wsp(block: WspWriter.() -> Unit): ByteArray =
        WspWriter().apply(block).toByteArray()

    private fun join(vararg pieces: ByteArray): ByteArray =
        ByteArray(pieces.sumOf { it.size }).also { out ->
            var offset = 0
            for (piece in pieces) {
                piece.copyInto(out, offset)
                offset += piece.size
            }
        }

    /** Short-integer for a constrained-media table index. */
    private fun media(index: Int) = byteArrayOf((0x80 or index).toByte())

    private fun text(value: String) = value.toByteArray(Charsets.US_ASCII)

    /** A WSP text-string, which is the unconstrained form of a media type. */
    private fun mediaText(value: String) = text(value) + byteArrayOf(0)

    private fun part(
        contentType: ByteArray,
        name: String? = null,
        contentId: String? = null,
        transferEncoding: String? = null,
        contentDisposition: ByteArray? = null,
        data: ByteArray = ByteArray(0),
    ): ByteArray {
        val headerBytes = wsp {
            appendValueLengthPrefixed {
                appendBytes(contentType)
                name?.let {
                    appendOctet(PduPart.NAME)
                    appendTextString(it)
                }
            }
            contentId?.let {
                appendOctet(PduPart.CONTENT_ID)
                appendQuotedString(it)
            }
            contentDisposition?.let { appendBytes(it) }
            transferEncoding?.let {
                appendOctet(0xC8)
                appendTextString(it)
            }
        }
        return wsp {
            appendUintvar(headerBytes.size.toLong())
            appendUintvar(data.size.toLong())
            appendBytes(headerBytes)
            appendBytes(data)
        }
    }

    private fun body(vararg parts: ByteArray): ByteArray =
        wsp {
            appendUintvar(parts.size.toLong())
            for (piece in parts) appendBytes(piece)
        }

    private val textPlain = media(ContentTypes.TABLE.indexOf("text/plain")!!)

    /** A minimal valid M-Send.req; [extra] lands between the From and the id. */
    private fun sendReq(
        extra: ByteArray = ByteArray(0),
        messageContentType: ByteArray = media(ContentTypes.TABLE.indexOf(ContentTypes.MULTIPART_RELATED)!!),
        bodyBytes: ByteArray,
    ): ByteArray = join(
        wsp {
            appendOctet(HeaderField.CONTENT_TYPE)
            appendValueLengthPrefixed { appendBytes(messageContentType) }
            appendOctet(HeaderField.MESSAGE_TYPE)
            appendOctet(MessageType.SEND_REQ)
            appendOctet(HeaderField.MMS_VERSION)
            appendShortInteger(HeaderField.MMS_VERSION_1_2)
            appendOctet(HeaderField.FROM)
            appendValueLength(1)
            appendOctet(EncodedStringValue.INSERT_ADDRESS_TOKEN)
        },
        extra,
        wsp {
            appendOctet(HeaderField.TRANSACTION_ID)
            appendTextString("T-probe")
        },
        bodyBytes,
    )

    private fun retrieveConf(
        retrieveStatus: Int?,
        messageContentType: ByteArray = media(ContentTypes.TABLE.indexOf(ContentTypes.MULTIPART_RELATED)!!),
        bodyBytes: ByteArray,
    ): ByteArray = join(
        wsp {
            appendOctet(HeaderField.CONTENT_TYPE)
            appendValueLengthPrefixed { appendBytes(messageContentType) }
            appendOctet(HeaderField.MESSAGE_TYPE)
            appendOctet(MessageType.RETRIEVE_CONF)
            appendOctet(HeaderField.MMS_VERSION)
            appendShortInteger(HeaderField.MMS_VERSION_1_2)
            appendOctet(HeaderField.DATE)
            appendLongInteger(now)
        },
        wsp {
            retrieveStatus?.let {
                appendOctet(HeaderField.RETRIEVE_STATUS)
                appendOctet(it)
            }
        },
        bodyBytes,
    )

    private fun parse(bytes: ByteArray, parseContentDisposition: Boolean = true) =
        PduParser(bytes, parseContentDisposition = parseContentDisposition, nowSeconds = { now })
            .parse()

    // --- The round trip that matters ---------------------------------------

    @Test
    fun aHandBuiltSendReqWithThreePartsParsesIntoItsHeaderAndParts() {
        val bytes = sendReq(
            extra = wsp {
                appendOctet(HeaderField.SUBJECT)
                appendEncodedStringValue(EncodedStringValue.utf8("Grüße"))
                appendOctet(HeaderField.DATE)
                appendLongInteger(now)
                appendOctet(HeaderField.PRIORITY)
                appendOctet(HeaderField.PRIORITY_HIGH)
                appendOctet(HeaderField.MESSAGE_CLASS)
                appendOctet(HeaderField.MESSAGE_CLASS_PERSONAL)
            },
            bodyBytes = body(
                part(mediaText(PduPart.APP_SMIL), "smil.xml", "<smil>", data = text("<smil/>")),
                part(media(ContentTypes.TABLE.indexOf("image/jpeg")!!), "pic.jpg", "<pic.jpg>", data = byteArrayOf(1, 2, 3, 4)),
                part(textPlain, "hello.txt", "<hello.txt>", data = text("hello")),
            ),
        )
        val pdu = parse(bytes)
        assertNotNull(pdu)
        assertEquals(MessageType.SEND_REQ, pdu!!.messageType)
        assertEquals(HeaderField.MMS_VERSION_1_2, pdu.mmsVersion)
        assertEquals("T-probe", pdu.transactionId)
        assertEquals("Grüße", pdu.subject!!.text)
        assertEquals(now, pdu.dateSeconds)
        assertEquals(HeaderField.PRIORITY_HIGH, pdu.headers.octetOrNull(HeaderField.PRIORITY))
        assertEquals(HeaderField.MESSAGE_CLASS_PERSONAL, pdu.messageClassOctet)
        assertEquals(EncodedStringValue.insertAddressToken(), pdu.from)
        assertEquals(ContentTypes.MULTIPART_RELATED, pdu.contentType)
        assertEquals(3, pdu.body!!.size)
        assertArrayEquals(text("<smil/>"), pdu.body!!.partAt(0)!!.data)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), pdu.body!!.partAt(1)!!.data)
        assertEquals("hello", pdu.body!!.textContent())
    }

    @Test
    fun aSendReqWithTwoRecipientsKeepsBothFieldOccurrencesApart() {
        val bytes = sendReq(
            extra = wsp {
                appendOctet(HeaderField.TO)
                appendEncodedStringValue(EncodedStringValue.utf8("+15551110000"))
                appendOctet(HeaderField.TO)
                appendEncodedStringValue(EncodedStringValue.utf8("+15552220000"))
            },
            bodyBytes = body(part(textPlain, "a.txt", data = text("a"))),
        )
        assertEquals(
            listOf("+15551110000", "+15552220000"),
            parse(bytes)!!.to.map { it.text },
        )
    }

    // --- §7 hostile input ---------------------------------------------------

    @Test
    fun aConstrainedCodePastTheTableDecodesToTheWildcard() {
        val bytes = sendReq(bodyBytes = body(part(media(127), "a.bin", data = text("a"))))
        assertEquals(ContentTypes.WILDCARD, parse(bytes)!!.body!!.partAt(0)!!.contentType)
    }

    @Test
    fun aConstrainedCodeAtTheLastTableIndexDecodesToThatTypeNotTheWildcard() {
        val lastIndex = ContentTypes.TABLE.size - 1
        val bytes = sendReq(bodyBytes = body(part(media(lastIndex), "a.bin", data = text("a"))))
        assertEquals("application/mikey", ContentTypes.TABLE[lastIndex])
        assertEquals("application/mikey", parse(bytes)!!.body!!.partAt(0)!!.contentType)
    }

    @Test
    fun aPartHeaderLengthShorterThanItsContentTypeIsRejected() {
        val headerBytes = wsp {
            appendValueLengthPrefixed { appendBytes(textPlain) }
        }
        val forged = wsp {
            appendUintvar(1)
            appendUintvar(0)
            appendBytes(headerBytes)
        }
        assertNull(parse(sendReq(bodyBytes = join(body(forged)))))
    }

    @Test
    fun aPartDataLengthBeyondTheBufferIsRejectedWithoutAllocating() {
        val headerBytes = wsp {
            appendValueLengthPrefixed {
                appendBytes(textPlain)
                appendOctet(PduPart.NAME)
                appendTextString("a.txt")
            }
        }
        val forged = wsp {
            appendUintvar(headerBytes.size.toLong())
            appendUintvar(0x7FFFFFFFL)
            appendBytes(headerBytes)
        }
        assertNull(parse(sendReq(bodyBytes = join(body(forged)))))
    }

    @Test
    fun anEntryCountLargerThanTheBufferIsRejected() {
        assertNull(parse(sendReq(bodyBytes = wsp { appendUintvar(0x7FFFFFFFL) })))
    }

    @Test
    fun anOversizedContentTypeValueLengthIsRejected() {
        val bytes = join(
            wsp {
                appendOctet(HeaderField.CONTENT_TYPE)
                appendValueLength(0x7FFFFFFF)
            },
            wsp {
                appendOctet(HeaderField.MESSAGE_TYPE)
                appendOctet(MessageType.NOTIFYRESP_IND)
            },
        )
        assertNull(parse(bytes))
    }

    @Test
    fun aFromValueLengthWithNoFollowingBytesIsRejected() {
        val bytes = join(
            wsp {
                appendOctet(HeaderField.MESSAGE_TYPE)
                appendOctet(MessageType.ACKNOWLEDGE_IND)
                appendOctet(HeaderField.MMS_VERSION)
                appendShortInteger(HeaderField.MMS_VERSION_1_2)
                appendOctet(HeaderField.FROM)
                appendValueLength(9)
            },
            wsp {
                appendOctet(HeaderField.TRANSACTION_ID)
                appendTextString("T")
            },
        )
        assertNull(parse(bytes))
    }

    @Test
    fun everyTruncationOfAValidPduIsRejectedAndNothingEscapes() {
        val notifyResp = join(
            wsp {
                appendOctet(HeaderField.MESSAGE_TYPE)
                appendOctet(MessageType.NOTIFYRESP_IND)
                appendOctet(HeaderField.MMS_VERSION)
                appendShortInteger(HeaderField.MMS_VERSION_1_2)
                appendOctet(HeaderField.STATUS)
                appendOctet(HeaderField.STATUS_EXPIRED)
            },
            wsp {
                appendOctet(HeaderField.TRANSACTION_ID)
                appendTextString("T-abc")
            },
        )
        for (length in 0 until notifyResp.size) {
            assertNull(
                "prefix of $length octets should not parse",
                parse(notifyResp.copyOfRange(0, length)),
            )
        }

        val send = sendReq(
            bodyBytes = body(
                part(textPlain, "a.txt", "<a.txt>", data = text("hello there")),
            ),
        )
        for (length in 0 until send.size) {
            assertNull(
                "prefix of $length octets should not parse",
                parse(send.copyOfRange(0, length)),
            )
        }
    }

    @Test
    fun anUnknownHeaderFieldIsSkippedAndTheFieldsAfterItStillParse() {
        val bytes = sendReq(
            extra = wsp {
                appendOctet(0xC0)
                appendTextString("unknown-carrier-field")
            },
            bodyBytes = body(part(textPlain, "a.txt", data = text("a"))),
        )
        val pdu = parse(bytes)
        assertNotNull(pdu)
        assertEquals("T-probe", pdu!!.transactionId)
        assertEquals(1, pdu.body!!.size)
    }

    @Test
    fun anUnknownPartHeaderFieldIsSkippedAndTheDataAfterItStillParses() {
        val headerBytes = wsp {
            appendValueLengthPrefixed {
                appendBytes(textPlain)
                appendOctet(PduPart.NAME)
                appendTextString("a.txt")
            }
            appendOctet(0x8F)
            appendTextString("pad")
        }
        val bytes = sendReq(
            bodyBytes = body(
                wsp {
                    appendUintvar(headerBytes.size.toLong())
                    appendUintvar(2)
                    appendBytes(headerBytes)
                    appendBytes(text("hi"))
                },
            ),
        )
        assertArrayEquals(text("hi"), parse(bytes)!!.body!!.partAt(0)!!.data)
    }

    @Test
    fun aMessageTypeThisVersionDoesNotSupportIsRejected() {
        for (type in listOf(0x89, 0x8A, 0x9C, 0x00, 0x7F)) {
            val bytes = wsp {
                appendOctet(HeaderField.MESSAGE_TYPE)
                appendOctet(type)
                appendOctet(HeaderField.MMS_VERSION)
                appendShortInteger(HeaderField.MMS_VERSION_1_2)
            }
            assertNull("type 0x%02X should be rejected".format(type), parse(bytes))
        }
    }

    @Test
    fun aMissingMmsVersionIsRejected() {
        val bytes = wsp {
            appendOctet(HeaderField.MESSAGE_TYPE)
            appendOctet(MessageType.ACKNOWLEDGE_IND)
            appendOctet(HeaderField.TRANSACTION_ID)
            appendTextString("T")
        }
        assertNull(parse(bytes))
    }

    // --- Field-level behaviour --------------------------------------------

    @Test
    fun theNoAddressFromFormDecodesToTheInsertAddressToken() {
        val bytes = sendReq(bodyBytes = body(part(textPlain, "a.txt", data = text("a"))))
        val from = parse(bytes)!!.from!!
        assertEquals(EncodedStringValue.INSERT_ADDRESS_TOKEN, from.charsetMibEnum)
        assertEquals(EncodedStringValue.INSERT_ADDRESS_TOKEN_STR, from.text)
    }

    @Test
    fun aFromWithAnAddressKeepsItsOwnCharset() {
        val bytes = sendReq(
            extra = wsp {
                appendOctet(HeaderField.FROM)
                appendValueLengthPrefixed {
                    appendOctet(EncodedStringValue.ADDRESS_PRESENT_TOKEN)
                    appendEncodedStringValue(EncodedStringValue.utf8("+15551230000"))
                }
            },
            bodyBytes = body(part(textPlain, "a.txt", data = text("a"))),
        )
        assertEquals("+15551230000", parse(bytes)!!.from!!.text)
    }

    @Test
    fun aRelativeExpiryResolvesAgainstTheInjectedClock() {
        val bytes = sendReq(
            extra = wsp {
                appendOctet(HeaderField.EXPIRY)
                appendValueLengthPrefixed {
                    appendOctet(HeaderField.VALUE_RELATIVE_TOKEN)
                    appendLongInteger(3600)
                }
            },
            bodyBytes = body(part(textPlain, "a.txt", data = text("a"))),
        )
        assertEquals(now + 3600, parse(bytes)!!.expirySeconds)
    }

    @Test
    fun anAbsoluteExpiryIsTakenAsGiven() {
        val bytes = sendReq(
            extra = wsp {
                appendOctet(HeaderField.EXPIRY)
                appendValueLengthPrefixed {
                    appendOctet(HeaderField.VALUE_ABSOLUTE_TOKEN)
                    appendLongInteger(1_234_567_890L)
                }
            },
            bodyBytes = body(part(textPlain, "a.txt", data = text("a"))),
        )
        assertEquals(1_234_567_890L, parse(bytes)!!.expirySeconds)
    }

    @Test
    fun aTokenTextMessageClassFallsBackToItsOctet() {
        val bytes = sendReq(
            extra = wsp {
                appendOctet(HeaderField.MESSAGE_CLASS)
                appendTextString(HeaderField.MESSAGE_CLASS_ADVERTISEMENT_STR)
            },
            bodyBytes = body(part(textPlain, "a.txt", data = text("a"))),
        )
        assertEquals(HeaderField.MESSAGE_CLASS_ADVERTISEMENT, parse(bytes)!!.messageClassOctet)
    }

    @Test
    fun aPartWithNoIdentifierGetsASynthesisedName() {
        val bytes = sendReq(bodyBytes = body(part(textPlain, name = null, data = text("a"))))
        assertEquals("part", parse(bytes)!!.body!!.partAt(0)!!.name)
    }

    @Test
    fun aNamelessSmilPartIsCalledSmil() {
        val bytes = sendReq(bodyBytes = body(part(mediaText(PduPart.APP_SMIL), name = null, data = text("<smil/>"))))
        assertEquals(PduPart.DEFAULT_SMIL_NAME, parse(bytes)!!.body!!.partAt(0)!!.name)
    }

    @Test
    fun multipartAlternativeIsFlattenedToItsFirstChild() {
        val alternative = media(ContentTypes.TABLE.indexOf(ContentTypes.MULTIPART_ALTERNATIVE)!!)
        val bytes = sendReq(
            messageContentType = alternative,
            bodyBytes = body(
                part(
                    alternative,
                    "wrapper",
                    data = body(
                        part(textPlain, "first.txt", "<first.txt>", data = text("chosen")),
                        part(media(ContentTypes.TABLE.indexOf("image/png")!!), "second.png", "<second.png>", data = text("ignored")),
                    ),
                ),
            ),
        )
        val parsed = parse(bytes)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.body!!.size)
        assertEquals("first.txt", parsed.body!!.partAt(0)!!.name)
        assertEquals("text/plain", parsed.body!!.partAt(0)!!.contentType)
        assertEquals("chosen", parsed.body!!.textContent())
    }

    @Test
    fun alternativesNestedDeeperThanARealMessageAreRejectedRatherThanRecursing() {
        val alternative = media(ContentTypes.TABLE.indexOf(ContentTypes.MULTIPART_ALTERNATIVE)!!)
        var nested = body(part(textPlain, "leaf.txt", "<leaf.txt>", data = text("deep")))
        repeat(40) {
            nested = body(part(alternative, "wrap", data = nested))
        }
        assertNull(parse(sendReq(bodyBytes = nested)))
    }

    @Test
    fun theStartParameterMovesItsPartToTheFrontOfTheBody() {
        val bytes = join(
            wsp {
                appendOctet(HeaderField.CONTENT_TYPE)
                appendValueLengthPrefixed {
                    appendBytes(media(ContentTypes.TABLE.indexOf(ContentTypes.MULTIPART_RELATED)!!))
                    appendOctet(0x8A)
                    appendTextString("<second.txt>")
                }
                appendOctet(HeaderField.MESSAGE_TYPE)
                appendOctet(MessageType.SEND_REQ)
                appendOctet(HeaderField.MMS_VERSION)
                appendShortInteger(HeaderField.MMS_VERSION_1_2)
                appendOctet(HeaderField.FROM)
                appendValueLength(1)
                appendOctet(EncodedStringValue.INSERT_ADDRESS_TOKEN)
                appendOctet(HeaderField.TRANSACTION_ID)
                appendTextString("T")
            },
            body(
                part(textPlain, "first.txt", "<first.txt>", data = text("one")),
                part(textPlain, "second.txt", "<second.txt>", data = text("two")),
            ),
        )
        val parsed = parse(bytes)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.body!!.size)
        assertEquals("second.txt", parsed.body!!.partAt(0)!!.name)
        assertEquals("first.txt", parsed.body!!.partAt(1)!!.name)
    }

    @Test
    fun base64TransferEncodedDataIsDecoded() {
        val bytes = sendReq(
            bodyBytes = body(
                part(textPlain, "a.txt", transferEncoding = PduPart.BASE64, data = text("aGVsbG8=")),
            ),
        )
        assertArrayEquals(text("hello"), parse(bytes)!!.body!!.partAt(0)!!.data)
    }

    @Test
    fun quotedPrintableTransferEncodedDataIsDecoded() {
        val bytes = sendReq(
            bodyBytes = body(
                part(textPlain, "a.txt", transferEncoding = PduPart.QUOTED_PRINTABLE, data = text("caf=C3=A9")),
            ),
        )
        val data = parse(bytes)!!.body!!.partAt(0)!!.data!!
        assertEquals("café", String(data, Charsets.UTF_8))
    }

    @Test
    fun anUnknownTransferEncodingLeavesTheDataAsSent() {
        val bytes = sendReq(
            bodyBytes = body(
                part(textPlain, "a.txt", transferEncoding = "x-uuencode", data = text("raw")),
            ),
        )
        assertArrayEquals(text("raw"), parse(bytes)!!.body!!.partAt(0)!!.data)
    }

    @Test
    fun contentDispositionIsReadWhenTheFieldIsWellFormed() {
        val disposition = wsp {
            appendOctet(0xC5)
            appendValueLengthPrefixed {
                appendOctet(0x81)
                appendOctet(0x86)
                appendTextString("a.txt")
            }
        }
        val bytes = sendReq(
            bodyBytes = body(part(textPlain, "a.txt", contentDisposition = disposition, data = text("a"))),
        )
        assertEquals("attachment", parse(bytes)!!.body!!.partAt(0)!!.contentDisposition)
    }

    @Test
    fun contentDispositionIsSkippedWhenTheCarrierGetsItWrong() {
        val disposition = wsp {
            appendOctet(0xC5)
            appendValueLengthPrefixed {
                appendOctet(0x81)
                appendOctet(0x86)
                appendTextString("a.txt")
            }
        }
        val bytes = sendReq(
            bodyBytes = body(part(textPlain, "a.txt", contentDisposition = disposition, data = text("a"))),
        )
        val parsed = parse(bytes, parseContentDisposition = false)
        assertNotNull(parsed)
        assertNull(parsed!!.body!!.partAt(0)!!.contentDisposition)
        assertArrayEquals(text("a"), parsed.body!!.partAt(0)!!.data)
    }

    // --- M-Retrieve.conf ----------------------------------------------------

    @Test
    fun aRetrieveConfWithAnOkStatusCarriesItsBody() {
        val bytes = retrieveConf(
            retrieveStatus = HeaderField.RETRIEVE_STATUS_OK,
            bodyBytes = body(part(textPlain, "a.txt", data = text("a"))),
        )
        val parsed = parse(bytes)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.body!!.size)
    }

    @Test
    fun aRetrieveConfThatOmitsTheRetrieveStatusStillCarriesItsBody() {
        val bytes = retrieveConf(retrieveStatus = null, bodyBytes = body(part(textPlain, "a.txt", data = text("a"))))
        assertNotNull(parse(bytes)!!.body)
    }

    @Test
    fun aRetrieveConfWithAFailedRetrieveStatusHasNoBody() {
        val bytes = retrieveConf(
            retrieveStatus = HeaderField.RETRIEVE_STATUS_ERROR_PERMANENT_FAILURE,
            bodyBytes = body(part(textPlain, "a.txt", data = text("a"))),
        )
        val parsed = parse(bytes)
        assertNotNull(parsed)
        assertNull(parsed!!.body)
        assertEquals(false, parsed.carriesContent)
    }

    @Test
    fun aRetrieveConfWithANonMultipartContentTypeIsRejected() {
        val bytes = retrieveConf(
            retrieveStatus = HeaderField.RETRIEVE_STATUS_OK,
            messageContentType = media(ContentTypes.TABLE.indexOf("text/plain")!!),
            bodyBytes = body(part(textPlain, "a.txt", data = text("a"))),
        )
        assertNull(parse(bytes))
    }

    @Test
    fun aSendReqUnderANonMultipartContentTypeIsRejected() {
        val bytes = sendReq(
            messageContentType = media(ContentTypes.TABLE.indexOf("text/plain")!!),
            bodyBytes = body(part(textPlain, "a.txt", data = text("a"))),
        )
        assertNull(parse(bytes))
    }

    @Test
    fun emptyDataIsNotTreatedAsMissingContent() {
        val bytes = sendReq(bodyBytes = body(part(textPlain, "a.txt", data = ByteArray(0))))
        val parsed = parse(bytes)
        assertNotNull(parsed)
        assertEquals(0, parsed!!.body!!.partAt(0)!!.data!!.size)
    }
}