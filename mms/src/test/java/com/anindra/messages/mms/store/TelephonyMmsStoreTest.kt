package com.anindra.messages.mms.store

import com.anindra.messages.mms.pdu.CharacterSets
import com.anindra.messages.mms.pdu.ContentTypes
import com.anindra.messages.mms.pdu.EncodedStringValue
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduBody
import com.anindra.messages.mms.pdu.PduPart
import android.net.Uri
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CLOCK = 1_700_000_000_000L

private val Uri0 = Uri.parse("content://mms/1")

private fun sendReq(
    messageType: Int = MessageType.SEND_REQ,
    body: PduBody? = null,
    configure: Pdu.() -> Unit = {},
): Pdu = Pdu(messageType).apply {
    headers.setOctet(HeaderField.MMS_VERSION, HeaderField.CURRENT_MMS_VERSION)
    headers.setContentType(ContentTypes.MULTIPART_RELATED)
    headers.setFrom(EncodedStringValue.utf8("+15550000001"))
    headers.addEncoded(HeaderField.TO, EncodedStringValue.utf8("+15550000002"))
    headers.setText(HeaderField.TRANSACTION_ID, "T-transaction")
    configure()
    this.body = body
}

private fun PduPart.named(name: String, contentType: String, data: ByteArray): PduPart = apply {
    this.name = name
    this.contentType = contentType
    this.data = data
}

class TelephonyMmsStoreTest {

    private val resolver = FakeContentResolver()
    private val store = TelephonyMmsStore(resolver, clock = { CLOCK })

    @Test
    fun persistWritesMessagePartsAndAddresses() {
        val body = PduBody().apply {
            add(
                PduPart().named("smil.xml", PduPart.APP_SMIL, "<smil/>".toByteArray())
            )
            add(PduPart().named("note.txt", "text/plain", "hello".toByteArray()))
        }

        val uri = store.persist(sendReq(body = body), MmsBox.OUTBOX, subscriptionId = 2)

        assertEquals("content://mms/outbox/1", uri!!.toString())
        assertEquals(1L, uri.lastPathSegment!!.toLong())
        val message = resolver.message(1)!!
        assertEquals(MmsBox.OUTBOX.value, message["msg_box"])
        assertEquals(MessageType.SEND_REQ, message["m_type"])
        assertEquals("T-transaction", message[TelephonyMmsStore.COLUMN_TRANSACTION_ID])
        assertEquals(2, message["sub_id"])
        assertEquals(2, resolver.partsOf(1).size)
        assertEquals(
            listOf(HeaderField.FROM, HeaderField.TO),
            resolver.addressesOf(1).map { it["type"] },
        )
    }

    @Test
    fun aMessageWhoseAttachmentCouldNotBeWrittenIsNotPersisted() {
        // The second part's data write fails after the first has landed, so
        // the store has to roll its own writing back rather than hand out a
        // message missing its attachment — the inbound path acknowledges
        // retrieval on any non-null result.
        resolver.failMatching = { it == "openOutputStream content://mms/part/101" }
        val body = PduBody().apply {
            add(PduPart().named("a.txt", "text/plain", "hello".toByteArray()))
            add(PduPart().named("photo.jpg", "image/jpeg", ByteArray(16)))
        }

        assertNull(store.persist(sendReq(body = body), MmsBox.INBOX, subscriptionId = 1))
        assertTrue(resolver.messages().isEmpty())
        assertTrue(resolver.partsOf(CLOCK).isEmpty())
    }

    @Test
    fun partsAreWrittenBeforeTheMessageRowAndRepointedAfterwards() {
        val body = PduBody().apply {
            add(PduPart().named("a.txt", "text/plain", "a".toByteArray()))
            add(PduPart().named("b.txt", "text/plain", "b".toByteArray()))
        }

        store.persist(sendReq(body = body), MmsBox.INBOX, subscriptionId = 1)

        val messageRow = resolver.operations.indexOf("insert content://mms/inbox")
        assertTrue("no message row insert", messageRow >= 0)
        val partRows = resolver.operations.withIndex()
            .filter { it.value.startsWith("insert content://mms/$CLOCK/part") }
            .map { it.index }
        assertEquals(2, partRows.size)
        assertTrue("a part was written after the message row", partRows.all { it < messageRow })
        val repoint = resolver.operations.indexOf("update content://mms/$CLOCK/part")
        assertTrue("mid was never corrected", repoint > messageRow)
        assertEquals(listOf(1L, 1L), resolver.partsOf(1).map { it["mid"] })
    }

    @Test
    fun jpegFaultAndVcardFaultAreRepaired() {
        val body = PduBody().apply {
            add(PduPart().named("photo.jpg", "image/jpg", byteArrayOf(1, 2)))
            add(PduPart().named("card.txt", "text/plain", "BEGIN:VCARD\nEND:VCARD".toByteArray()))
            add(PduPart().named("note.txt", "text/plain", "plain".toByteArray()))
        }

        store.persist(sendReq(body = body), MmsBox.INBOX, subscriptionId = 1)

        assertEquals(
            listOf("image/jpeg", "text/x-vCard", "text/plain"),
            resolver.partsOf(1).map { it["ct"] },
        )
    }

    @Test
    fun smilSortsAheadOfTheStartPart() {
        val body = PduBody().apply {
            add(PduPart().named("photo.jpg", "image/jpeg", byteArrayOf(9)))
            add(PduPart().named("smil.xml", PduPart.APP_SMIL, "<smil/>".toByteArray()))
        }

        store.persist(sendReq(body = body), MmsBox.INBOX, subscriptionId = 1)

        assertEquals(
            listOf(null, -1),
            resolver.partsOf(1).map { it["seq"] },
        )
    }

    @Test
    fun textGoesToAColumnAndBinaryIsStreamed() {
        val body = PduBody().apply {
            add(PduPart().named("smil.xml", PduPart.APP_SMIL, "<smil/>".toByteArray()))
            add(PduPart().named("note.txt", "text/plain", "in a column".toByteArray()))
            add(PduPart().named("photo.jpg", "image/jpeg", byteArrayOf(7, 8, 9)))
        }

        store.persist(sendReq(body = body), MmsBox.INBOX, subscriptionId = 1)

        val (smil, text, image) = resolver.partsOf(1)
        assertEquals("<smil/>", smil["text"])
        assertEquals("in a column", text["text"])
        assertNull("an attachment was buffered into the text column", image["text"])
        assertArrayEquals(byteArrayOf(7, 8, 9), resolver.blobOf(image["_id"] as Long))
        assertEquals(
            "openOutputStream content://mms/part/${image["_id"]}",
            resolver.operations.single { it.startsWith("openOutputStream") },
        )
    }

    @Test
    fun loadRebuildsThePduThatWasPersisted() {
        val body = PduBody().apply {
            add(PduPart().named("smil.xml", PduPart.APP_SMIL, "<smil/>".toByteArray()))
            add(PduPart().named("note.txt", "text/plain", "round trip".toByteArray()))
            add(PduPart().named("photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3)))
        }
        val original = sendReq(body = body) {
            headers.setLong(HeaderField.DATE, 1_699_999_999)
            headers.setLong(HeaderField.MESSAGE_SIZE, 42)
            headers.setText(HeaderField.CONTENT_LOCATION, "mmsc://carrier/1")
            headers.setEncoded(HeaderField.SUBJECT, EncodedStringValue(CharacterSets.ISO_8859_1, "Grüße"))
            headers.setMessageClassText(HeaderField.MESSAGE_CLASS_PERSONAL_STR)
            headers.setOctet(HeaderField.PRIORITY, HeaderField.PRIORITY_HIGH)
            headers.addEncoded(HeaderField.CC, EncodedStringValue.utf8("+15550000003"))
        }

        val uri = store.persist(original, MmsBox.SENT, subscriptionId = 1)!!
        val loaded = store.load(uri)!!

        assertEquals(MessageType.SEND_REQ, loaded.messageType)
        assertEquals(HeaderField.CURRENT_MMS_VERSION, loaded.mmsVersion)
        assertEquals("T-transaction", loaded.transactionId)
        assertEquals(ContentTypes.MULTIPART_RELATED, loaded.contentType)
        assertEquals("mmsc://carrier/1", loaded.contentLocation)
        assertEquals(1_699_999_999L, loaded.dateSeconds)
        assertEquals(42L, loaded.messageSize)
        assertEquals(
            HeaderField.PRIORITY_HIGH,
            loaded.headers.octetOrNull(HeaderField.PRIORITY),
        )
        assertEquals(HeaderField.MESSAGE_CLASS_PERSONAL, loaded.messageClassOctet)
        assertEquals("Grüße", loaded.subject?.text)
        assertEquals(CharacterSets.ISO_8859_1, loaded.subject?.charsetMibEnum)
        assertEquals("+15550000001", loaded.from?.text)
        assertEquals(listOf("+15550000002"), loaded.to.map { it.text })
        assertEquals(listOf("+15550000003"), loaded.cc.map { it.text })

        val loadedParts = loaded.body!!.parts()
        assertEquals(3, loadedParts.size)
        assertEquals("smil.xml", loadedParts[0].name)
        assertEquals(PduPart.APP_SMIL, loadedParts[0].contentType)
        assertEquals("<smil/>", String(loadedParts[0].data!!))
        assertEquals("round trip", String(loadedParts[1].data!!))
        assertArrayEquals(byteArrayOf(1, 2, 3), loadedParts[2].data)
    }

    @Test
    fun moveTouchesOnlyTheMessageBox() {
        val uri = store.persist(sendReq(), MmsBox.OUTBOX, subscriptionId = 1)!!
        val before = resolver.message(1)!!.toMap()

        val moved = store.move(uri, MmsBox.SENT)

        assertEquals("content://mms/sent/1", moved.toString())
        val after = resolver.message(1)!!
        assertEquals(MmsBox.SENT.value, after["msg_box"])
        assertEquals(before.keys, after.keys)
        assertEquals(
            before.filterKeys { it != "msg_box" }.mapValues { it.value },
            after.filterKeys { it != "msg_box" },
        )
    }

    @Test
    fun aProviderFailureBecomesNullOrFalseInsteadOfEscaping() {
        val failing = TelephonyMmsStore(
            resolver.apply { failMatching = { true } },
            clock = { CLOCK },
        )

        assertNull(failing.persist(sendReq(), MmsBox.INBOX, 1))
        assertNull(failing.load(Uri0))
        assertNull(failing.move(Uri0, MmsBox.SENT))
        assertFalse(failing.updateMessageBox(Uri0, MmsBox.SENT))
        assertFalse(failing.delete(Uri0))
        assertFalse(failing.setRead(Uri0, true, true))
        assertNull(failing.openPartStream(Uri0))
        assertNull(failing.partBytes(Uri0))
        assertNull(failing.createThreadId(listOf("+15550000002")))
        assertTrue(failing.pendingMessages(0).isEmpty())
        assertFalse(failing.setPendingErrorType(Uri0, 1))
        assertFalse(failing.deletePending(Uri0))
    }

    @Test
    fun subIdProbeRunsOnceAndItsAnswerIsReused() {
        val withColumn = TelephonyMmsStore(resolver, clock = { CLOCK })
        withColumn.persist(sendReq(), MmsBox.INBOX, subscriptionId = 3)
        withColumn.persist(sendReq(), MmsBox.INBOX, subscriptionId = 3)

        val probes = resolver.operations.count { it == "query content://mms" }
        assertEquals("the probe was repeated", 1, probes)
        assertTrue(resolver.messages().all { it["sub_id"] == 3 })

        val withoutColumn = FakeContentResolver(hasSubIdColumn = false)
        val store = TelephonyMmsStore(withoutColumn, clock = { CLOCK })
        assertNotNull(store.persist(sendReq(), MmsBox.INBOX, subscriptionId = 3))
        assertEquals(
            "sub_id was written to a provider without the column",
            emptyList<Map<String, Any?>>(),
            withoutColumn.messages().filter { it.containsKey("sub_id") },
        )
    }

    @Test
    fun threadIdExcludesThisDeviceAndUsesThePlatformComparison() {
        val store = TelephonyMmsStore(
            resolver,
            clock = { CLOCK },
            lineOneNumber = { "+1 555 000 0002" },
            numbersEqual = { a, b -> a.filter(Char::isDigit) == b.filter(Char::isDigit) },
        )
        val pdu = Pdu(MessageType.RETRIEVE_CONF).apply {
            headers.setOctet(HeaderField.MMS_VERSION, HeaderField.CURRENT_MMS_VERSION)
            headers.setContentType(ContentTypes.MULTIPART_RELATED)
            headers.setFrom(EncodedStringValue.utf8("+15550000001"))
            headers.addEncoded(HeaderField.TO, EncodedStringValue.utf8("+15550000002"))
            headers.addEncoded(HeaderField.TO, EncodedStringValue.utf8("+15550000004"))
            headers.addEncoded(HeaderField.CC, EncodedStringValue.utf8("+15550000005"))
        }

        store.persist(pdu, MmsBox.INBOX, subscriptionId = 1)

        assertEquals(500L, resolver.message(1)!!["thread_id"])
        assertEquals(
            listOf("+15550000001", "+15550000004", "+15550000005"),
            resolver.queriedThreadParticipants().single(),
        )
    }

    @Test
    fun aSoleInboundAddressIsOursWhenTheLineNumberIsUnknown() {
        val store = TelephonyMmsStore(resolver, clock = { CLOCK }, lineOneNumber = { null })
        val pdu = Pdu(MessageType.RETRIEVE_CONF).apply {
            headers.setFrom(EncodedStringValue.utf8("+15550000001"))
            headers.addEncoded(HeaderField.TO, EncodedStringValue.utf8("+15550000002"))
        }

        store.persist(pdu, MmsBox.INBOX, subscriptionId = 1)

        assertEquals(listOf("+15550000001"), resolver.queriedThreadParticipants().single())
    }

    @Test
    fun onlyConversationTypesResolveAThread() {
        store.persist(sendReq(MessageType.ACKNOWLEDGE_IND), MmsBox.INBOX, subscriptionId = 1)

        assertEquals(emptyList<List<String>>(), resolver.queriedThreadParticipants())
        assertNull(resolver.message(1)!!["thread_id"])
    }
}
