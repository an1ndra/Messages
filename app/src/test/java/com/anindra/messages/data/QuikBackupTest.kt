package com.anindra.messages.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The QUIK (QKSMS) backup shape, taken from a real file written by QUIK 4.3.7
 * on emulator-5558:
 *
 * ```json
 * {"messageCount":101,"messages":[{"type":1,"address":"+1555444329",
 *  "date":1790279506001,"dateSent":0,"read":true,"status":0,
 *  "body":"imported chat nc…","protocol":0,"locked":false,"subId":-1}]}
 * ```
 *
 * Two things differ from the sms-ie records the importer already reads: the
 * messages sit under a wrapper object, and `read` is a boolean rather than 0/1.
 * Both are handled by converting to the sms-ie shape and reusing that importer.
 */
class QuikBackupTest {

    private val sample = """
        {"messageCount":3,"messages":[
          {"type":1,"address":"+1555444329","date":1790279506001,"dateSent":0,
           "read":true,"status":0,"body":"imported chat","protocol":0,
           "locked":false,"subId":-1},
          {"type":2,"address":"+15556790","date":1790279600000,"dateSent":1790279599000,
           "read":false,"status":0,"body":"sent one","protocol":0,
           "locked":true,"subId":-1},
          {"type":1,"address":"","date":1790279700000,"read":false,"body":"no address"}
        ]}
    """.trimIndent()

    @Test
    fun theWrapperShapeIsRecognised() {
        assertTrue(QuikBackup.looksLikeQuik(sample))
        assertFalse(QuikBackup.looksLikeQuik("""[{"address":"+1555","body":"sms-ie"}]"""))
    }

    @Test
    fun everyMessageTheFileHoldsBecomesARecord() {
        val written = mutableListOf<String>()
        val count = QuikBackup.toRecords(JSONObject(sample)) { written.add(it) }
        // The third record has no address, so it cannot become a message.
        assertEquals(2, count)
        assertEquals(2, written.size)
    }

    @Test
    fun theReadFlagIsConvertedFromBooleanToZeroOne() {
        val messages = JSONObject(sample).getJSONArray("messages")
        assertEquals(1, QuikBackup.toRecord(messages.getJSONObject(0))!!.getInt("read"))
        assertEquals(0, QuikBackup.toRecord(messages.getJSONObject(1))!!.getInt("read"))
    }

    @Test
    fun theFieldsSmsIeCarriesAreAllKept() {
        val record = QuikBackup.toRecord(
            JSONObject(sample).getJSONArray("messages").getJSONObject(1)
        )!!
        assertEquals("+15556790", record.getString("address"))
        assertEquals("sent one", record.getString("body"))
        assertEquals(1790279600000L, record.getLong("date"))
        assertEquals(1790279599000L, record.getLong("date_sent"))
        assertEquals(2, record.getInt("type"))
        assertEquals(1, record.getInt("locked"))
    }

    @Test
    fun anUnusableMessageIsSkippedNotFatal() {
        assertNull(QuikBackup.toRecord(JSONObject("""{"address":"","body":"x","date":1}""")))
        assertNull(QuikBackup.toRecord(JSONObject("""{"address":"+1555","body":"x"}""")))
    }

    @Test
    fun theConvertedRecordsParseBackThroughTheSmsIeReader() {
        // The point of the conversion is that the existing importer reads them.
        val messages = JSONObject(sample).getJSONArray("messages")
        val first = QuikBackup.toRecord(messages.getJSONObject(0))!!.toString()
        val parsed = SmsIeBackup.record(first)!!
        assertEquals("+1555444329", parsed.address)
        assertTrue(parsed.read)
        assertFalse(parsed.isMe)
        assertEquals("received", parsed.status)

        val second = QuikBackup.toRecord(messages.getJSONObject(1))!!.toString()
        val sent = SmsIeBackup.record(second)!!
        assertTrue(sent.isMe)
        assertEquals("sent", sent.status)
    }
}
