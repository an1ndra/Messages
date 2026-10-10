package com.anindra.messages.mms.store

import android.net.Uri
import android.provider.BaseColumns
import android.provider.Telephony.MmsSms.PendingMessages
import com.anindra.messages.mms.pdu.EncodedStringValue
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduBody
import com.anindra.messages.mms.pdu.PduPart
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of [MmsStore] that are about the contract itself — the box values,
 * the pending queue, part bytes and the read/delete verbs — as opposed to the
 * write path [TelephonyMmsStoreTest] covers.
 */
class MmsStoreContractTest {

    private val resolver = FakeContentResolver()
    private val store = TelephonyMmsStore(resolver)

    @Test
    fun boxValuesAreTheProviders() {
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5),
            MmsBox.entries.map { it.value },
        )
        MmsBox.entries.forEach { assertEquals(it, MmsBox.of(it.value)) }
        assertNull(MmsBox.of(6))
    }

    @Test
    fun fiveIsFailedAndNotATemporaryBox() {
        // The SDK has no temporary box. Reading 5 as one marks the message failed,
        // which is how a queued-but-unsent message gets lost.
        assertEquals(5, MmsBox.FAILED.value)
        assertEquals(android.provider.Telephony.Mms.MESSAGE_BOX_FAILED, MmsBox.FAILED.value)
        assertNull(MmsBox.entries.firstOrNull { it.name == "TEMP" })
    }

    @Test
    fun onlyTheBoxesWithACollectionHaveAUriPath() {
        assertEquals(
            listOf("inbox", "sent", "drafts", "outbox"),
            MmsBox.entries.filter { it.isAddressable }.map { it.path },
        )
        assertEquals(listOf("inbox", "sent", "drafts", "outbox"), MmsBox.persistable.map { it.path })
        assertNull(MmsBox.FAILED.path)
        assertNull(MmsBox.ALL.path)
    }

    @Test
    fun eachAddressableBoxPersistsIntoItsOwnUriPath() {
        var expectedId = 0L
        MmsBox.persistable.forEach { box ->
            expectedId++
            val uri = store.persist(Pdu(MessageType.SEND_REQ), box, subscriptionId = 1)
            assertEquals("content://mms/${box.path}/$expectedId", uri.toString())
        }
    }

    @Test
    fun persistingIntoABoxWithNoCollectionIsRefusedRatherThanGuessed() {
        assertNull(store.persist(Pdu(MessageType.SEND_REQ), MmsBox.FAILED, subscriptionId = 1))
        assertNull(store.persist(Pdu(MessageType.SEND_REQ), MmsBox.ALL, subscriptionId = 1))
    }

    @Test
    fun movingToFailedKeepsTheRowAtItsExistingUri() {
        val stored = store.persist(Pdu(MessageType.SEND_REQ), MmsBox.OUTBOX, subscriptionId = 1)!!
        val moved = store.move(stored, MmsBox.FAILED)!!
        assertEquals(stored, moved)
        assertTrue(store.updateMessageBox(stored, MmsBox.FAILED))
    }

    @Test
    fun pendingQueueAsksForDueRetryableRowsOnly() {
        resolver.seedPendingRow(
            mapOf(
                PendingMessages.MSG_ID to 11L,
                PendingMessages.DUE_TIME to 5_000L,
                PendingMessages.ERROR_TYPE to 1,
                PendingMessages.RETRY_INDEX to 2,
                TelephonyMmsStore.COLUMN_TRANSACTION_ID to "T-late",
                TelephonyMmsStore.COLUMN_MESSAGE_SIZE to 5L,
            ),
        )
        resolver.seedPendingRow(
            mapOf(
                PendingMessages.MSG_ID to 12L,
                PendingMessages.DUE_TIME to 2_000L,
                PendingMessages.ERROR_TYPE to 1,
                PendingMessages.RETRY_INDEX to 0,
                TelephonyMmsStore.COLUMN_TRANSACTION_ID to "T-due",
                TelephonyMmsStore.COLUMN_MESSAGE_SIZE to 6L,
            ),
        )
        resolver.seedPendingRow(
            mapOf(
                PendingMessages.MSG_ID to 13L,
                PendingMessages.DUE_TIME to 3_000L,
                PendingMessages.ERROR_TYPE to 128,
                PendingMessages.RETRY_INDEX to 0,
                TelephonyMmsStore.COLUMN_TRANSACTION_ID to "T-permanently-failed",
                TelephonyMmsStore.COLUMN_MESSAGE_SIZE to 7L,
            ),
        )

        val pending = store.pendingMessages(2_000)

        assertEquals(listOf(12L), pending.map { it.messageId })
        val message = pending.single()
        assertEquals("T-due", message.transactionId)
        assertEquals(6L, message.messageSize)
        assertEquals(2_000L, message.dueTimeSeconds)
        assertEquals(1, message.errorType)
        assertEquals(0, message.retryIndex)
        assertEquals("content://mms-sms/pending/901", message.uri.toString())

        val query = resolver.queries().last()
        assertEquals("content://mms-sms/pending", query.uri)
        assertEquals("err_type < ? AND due_time <= ?", query.selection)
        assertArrayEquals(arrayOf("128", "2000"), query.selectionArgs)
    }

    @Test
    fun pendingRowsAreOrderedByDueTime() {
        listOf(3_000L, 1_000L, 2_000L).forEach { due ->
            resolver.seedPendingRow(
                mapOf(
                    PendingMessages.MSG_ID to due,
                    PendingMessages.DUE_TIME to due,
                    PendingMessages.ERROR_TYPE to 1,
                    PendingMessages.RETRY_INDEX to 0,
                ),
            )
        }

        assertEquals(listOf(1_000L, 2_000L, 3_000L), store.pendingMessages(9_000).map { it.dueTimeSeconds })
    }

    @Test
    fun pendingErrorTypeIsWrittenAndTheRowCanBeDropped() {
        resolver.seedPendingRow(
            mapOf(
                PendingMessages.MSG_ID to 12L,
                PendingMessages.DUE_TIME to 1_000L,
                PendingMessages.ERROR_TYPE to 1,
                PendingMessages.RETRY_INDEX to 0,
            ),
        )
        val uri = Uri.parse("content://mms-sms/pending/900")

        assertTrue(store.setPendingErrorType(uri, 127))
        assertEquals(127, resolver.pendingRows().single()[PendingMessages.ERROR_TYPE])
        assertTrue(store.deletePending(uri))
        assertEquals(emptyList<Map<String, Any?>>(), resolver.pendingRows())
    }

    @Test
    fun partBytesRoundTripThroughTheProviderStream() {
        val uri = store.persist(
            Pdu(MessageType.RETRIEVE_CONF).apply {
                headers.setFrom(EncodedStringValue.utf8("+15550000001"))
                headers.setContentType("application/vnd.wap.multipart.related")
                body = PduBody().apply {
                    add(
                        PduPart().apply {
                            name = "photo.jpg"
                            contentType = "image/jpeg"
                            data = byteArrayOf(1, 2, 3, 4)
                        },
                    )
                }
            },
            MmsBox.INBOX,
            subscriptionId = 1,
        )!!
        val messageId = uri.lastPathSegment!!.toLong()
        val partUri = Uri.parse(
            "content://mms/part/" + resolver.partsOf(messageId).single()[BaseColumns._ID]
        )

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), store.partBytes(partUri))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), store.openPartStream(partUri)!!.readBytes())
    }

    @Test
    fun readingAndDeletingAMessageTouchTheProviderRow() {
        val uri = store.persist(Pdu(MessageType.SEND_REQ), MmsBox.INBOX, subscriptionId = 1)!!

        assertTrue(store.setRead(uri, read = true, seen = true))
        assertEquals(1, resolver.message(1)!!["read"])
        assertEquals(1, resolver.message(1)!!["seen"])
        assertTrue(store.setRead(uri, read = false, seen = false))
        assertEquals(0, resolver.message(1)!!["read"])
        assertEquals(0, resolver.message(1)!!["seen"])

        assertTrue(store.delete(uri))
        assertNull(resolver.message(1))
        assertFalse(store.delete(uri))
    }

    @Test
    fun updatingTheBoxOfAMessageThatIsNotThereReportsFailure() {
        assertFalse(store.updateMessageBox(Uri.parse("content://mms/404"), MmsBox.SENT))
        assertNull(store.move(Uri.parse("content://mms/inbox"), MmsBox.SENT))
    }

    @Test
    fun aMessageWithoutAThreadOrSubscriptionIdOmitsThoseColumns() {
        store.persist(Pdu(MessageType.ACKNOWLEDGE_IND), MmsBox.DRAFTS, subscriptionId = -1)

        val message = resolver.message(1)!!
        assertNull(message["thread_id"])
        assertNull(message["sub_id"])
        assertEquals(MmsBox.DRAFTS.value, message["msg_box"])
    }

    @Test
    fun headersRoundTripThroughTheMappedColumns() {
        val original = Pdu(MessageType.SEND_CONF).apply {
            headers.setText(HeaderField.TRANSACTION_ID, "T-conf")
            headers.setOctet(HeaderField.RESPONSE_STATUS, HeaderField.RESPONSE_STATUS_OK)
            headers.setEncoded(HeaderField.RETRIEVE_TEXT, EncodedStringValue.utf8("failed"))
            headers.setText(HeaderField.RESPONSE_TEXT, "server said no")
            headers.setText(HeaderField.MESSAGE_ID, "<m-1@example.com>")
            headers.setLong(HeaderField.DELIVERY_TIME, 1_699_999_000)
            headers.setOctet(HeaderField.STATUS, HeaderField.STATUS_RETRIEVED)
            headers.setOctet(HeaderField.READ_STATUS, HeaderField.READ_STATUS_READ)
            headers.setOctet(HeaderField.DELIVERY_REPORT, HeaderField.VALUE_YES)
            headers.setOctet(HeaderField.REPORT_ALLOWED, HeaderField.VALUE_NO)
            headers.setOctet(HeaderField.CONTENT_CLASS, HeaderField.MESSAGE_CLASS_PERSONAL)
        }

        val uri = store.persist(original, MmsBox.SENT, subscriptionId = 1)!!
        val loaded = store.load(uri)!!

        assertEquals("T-conf", loaded.transactionId)
        assertEquals(HeaderField.RESPONSE_STATUS_OK, loaded.responseStatus)
        assertEquals("failed", loaded.headers.encodedOrNull(HeaderField.RETRIEVE_TEXT)?.text)
        assertEquals("server said no", loaded.headers.textOrNull(HeaderField.RESPONSE_TEXT))
        assertEquals("<m-1@example.com>", loaded.headers.textOrNull(HeaderField.MESSAGE_ID))
        assertEquals(1_699_999_000L, loaded.headers.longOrNull(HeaderField.DELIVERY_TIME))
        assertEquals(HeaderField.STATUS_RETRIEVED, loaded.status)
        assertEquals(HeaderField.READ_STATUS_READ, loaded.headers.octetOrNull(HeaderField.READ_STATUS))
        assertEquals(HeaderField.VALUE_YES, loaded.headers.octetOrNull(HeaderField.DELIVERY_REPORT))
        assertEquals(HeaderField.VALUE_NO, loaded.headers.octetOrNull(HeaderField.REPORT_ALLOWED))
        assertEquals(
            HeaderField.MESSAGE_CLASS_PERSONAL,
            loaded.headers.octetOrNull(HeaderField.CONTENT_CLASS),
        )
    }

    @Test
    fun messageSizeDefaultsToTheSumOfTheParts() {
        val body = PduBody().apply {
            add(
                PduPart().apply {
                    name = "a.txt"
                    contentType = "text/plain"
                    data = ByteArray(10)
                },
            )
            add(
                PduPart().apply {
                    name = "b.bin"
                    contentType = "application/octet-stream"
                    data = ByteArray(7)
                },
            )
        }

        val uri = store.persist(Pdu(MessageType.SEND_REQ).apply { this.body = body }, MmsBox.OUTBOX, 1)

        assertEquals(17L, resolver.message(uri!!.lastPathSegment!!.toLong())!!["m_size"])
    }

    @Test
    fun loadOfSomethingThatIsNotAMessageIsNull() {
        assertNull(store.load(Uri.parse("content://mms/404")))
        assertNull(store.load(Uri.parse("content://mms/inbox")))
        assertNull(store.partBytes(Uri.parse("content://mms/part/1")))
    }
}
