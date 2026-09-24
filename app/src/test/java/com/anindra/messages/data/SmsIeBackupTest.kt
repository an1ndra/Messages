package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsIeBackupTest {

    @Test
    fun mapsSmsTypeToDirectionAndStatus() {
        assertEquals(false to "received", SmsIeBackup.smsStatus(1))
        assertEquals(true to "sent", SmsIeBackup.smsStatus(2))
        assertEquals(true to "sending", SmsIeBackup.smsStatus(4))
        assertEquals(true to "failed", SmsIeBackup.smsStatus(5))
    }

    @Test
    fun mapsMmsBoxToDirectionAndStatus() {
        assertEquals(false to "received", SmsIeBackup.mmsStatus(1))
        assertEquals(true to "sent", SmsIeBackup.mmsStatus(2))
        assertEquals(true to "sending", SmsIeBackup.mmsStatus(4))
    }

    @Test
    fun mmsDatesAreSecondsWhileSmsDatesAreMilliseconds() {
        assertEquals(1_700_000_000_000L, SmsIeBackup.normalizeTimestamp(1_700_000_000L, isMms = true))
        assertEquals(1_700_000_000_000L, SmsIeBackup.normalizeTimestamp(1_700_000_000_000L, isMms = true))
        assertEquals(1_700_000_000_000L, SmsIeBackup.normalizeTimestamp(1_700_000_000_000L, isMms = false))
    }

    @Test
    fun readsV1JsonArrayOfSms() {
        val json = """
            [
              {"address":"+15551230001","body":"hello v1","date":1700000000000,"type":1,"read":1},
              {"address":"+15551230002","body":"sent v1","date":1700000001000,"type":2,"read":1}
            ]
        """.trimIndent()
        val parsed = SmsIeBackup.parse(json)
        assertEquals(1, parsed.version)
        assertEquals(2, parsed.messages.size)
        val received = parsed.messages[0]
        assertEquals("+15551230001", received.address)
        assertEquals("hello v1", received.body)
        assertEquals(false, received.isMe)
        assertEquals("received", received.status)
        assertTrue(parsed.messages[1].isMe)
        assertEquals("sent", parsed.messages[1].status)
    }

    @Test
    fun readsV2NdjsonAndJoinsMmsParts() {
        // NDJSON: every record must be a single line.
        val json = "{\"_id\":\"1\",\"address\":\"+15551230003\",\"body\":\"v2 sms\",\"date\":1700000000000,\"type\":1,\"read\":1}\n" +
            "{\"_id\":\"2\",\"m_type\":132,\"msg_box\":1,\"date\":1700000000,\"read\":1," +
            "\"__sender_address\":{\"address\":\"+15551230004\",\"type\":137},\"__recipient_addresses\":[]," +
            "\"__parts\":[{\"ct\":\"text/plain\",\"text\":\"mms caption\"}," +
            "{\"ct\":\"image/jpeg\",\"_data\":\"/data/media/1/PART_7.jpg\"}]}"
        val parsed = SmsIeBackup.parse(json) { name ->
            if (name == "PART_7.jpg") byteArrayOf(1, 2, 3) else null
        }
        assertEquals(2, parsed.version)
        assertEquals(2, parsed.messages.size)
        val mms = parsed.messages[1]
        assertTrue(mms.isMms)
        assertEquals("+15551230004", mms.address)
        assertEquals("mms caption", mms.body)
        assertEquals(1_700_000_000_000L, mms.timestamp)
        assertNotNull(mms.imageBytes)
        assertEquals(3, mms.imageBytes!!.size)
        assertEquals("image/jpeg", mms.imageMime)
        assertEquals("PART_7.jpg", mms.imageName)
    }

    @Test
    fun readsSentMmsFromRecipientAddress() {
        val json = "{\"m_type\":128,\"msg_box\":2,\"date\":1700000000,\"read\":1," +
            "\"__sender_address\":{\"address\":\"+15550001111\",\"type\":137}," +
            "\"__recipient_addresses\":[{\"address\":\"+15552223333\",\"type\":151}]," +
            "\"__parts\":[{\"ct\":\"text/plain\",\"text\":\"sent mms\"}]}"
        val mms = SmsIeBackup.parse(json).messages.single()
        assertTrue(mms.isMe)
        assertEquals("+15552223333", mms.address)
        assertEquals("sent mms", mms.body)
        assertNull(mms.imageBytes)
    }

    @Test
    fun skipsRecordsWithoutAnAddress() {
        val json = "{\"body\":\"orphan\",\"date\":1700000000000,\"type\":1}\n" +
            "{\"address\":\"+15559990000\",\"body\":\"kept\",\"date\":1700000000000,\"type\":1}"
        val parsed = SmsIeBackup.parse(json)
        assertEquals(1, parsed.messages.size)
        assertEquals("+15559990000", parsed.messages.single().address)
    }

    @Test
    fun mmsTextFallsBackToPartDataFile() {
        val json = "{\"m_type\":132,\"msg_box\":1,\"date\":1700000000,\"read\":1," +
            "\"__sender_address\":{\"address\":\"+15551230009\",\"type\":137}," +
            "\"__parts\":[{\"ct\":\"text/plain\",\"_data\":\"/data/media/1/PART_2.txt\"}]}"
        val mms = SmsIeBackup.parse(json) { if (it == "PART_2.txt") "from file".toByteArray() else null }
            .messages.single()
        assertEquals("from file", mms.body)
    }

    @Test
    fun readsRealExportShapeWhereEveryNumberIsAString() {
        // Verified against a real sms-ie v2.11.1 export: the provider columns
        // are dumped verbatim, so date/type/read/m_type/msg_box are all strings.
        val json = "{\"_id\":\"498\",\"thread_id\":\"164\",\"address\":\"+15556663111\"," +
            "\"date\":\"1790279506000\",\"read\":\"0\",\"type\":\"1\"," +
            "\"body\":\"smsie real message 3 1790279506\",\"sub_id\":\"1\"}\n" +
            "{\"_id\":\"22\",\"thread_id\":\"143\",\"date\":\"1790269630000\",\"msg_box\":\"1\"," +
            "\"read\":\"1\",\"m_type\":\"132\"," +
            "\"__sender_address\":{\"address\":\"+15554449630\",\"type\":\"137\"}," +
            "\"__recipient_addresses\":[]," +
            "\"__parts\":[{\"ct\":\"image/png\",\"_data\":\"/data/user_de/0/x/app_parts/PART_7.png\"}]}"
        val parsed = SmsIeBackup.parse(json) { name ->
            if (name == "PART_7.png") byteArrayOf(9, 8, 7) else null
        }
        assertEquals(2, parsed.messages.size)

        val sms = parsed.messages[0]
        assertEquals("+15556663111", sms.address)
        assertEquals(1_790_279_506_000L, sms.timestamp)
        assertEquals(false, sms.isMe)
        assertEquals("received", sms.status)
        assertEquals(false, sms.read)

        val mms = parsed.messages[1]
        assertTrue(mms.isMms)
        assertEquals("+15554449630", mms.address)
        assertEquals(1_790_269_630_000L, mms.timestamp)
        assertEquals("received", mms.status)
        assertEquals("image/png", mms.imageMime)
        assertEquals(3, mms.imageBytes!!.size)
    }

    @Test
    fun namesExtensionsFromMime() {
        assertEquals("png", SmsIeBackup.extensionFor("image/png"))
        assertEquals("jpg", SmsIeBackup.extensionFor("image/jpeg"))
        assertEquals("mp4", SmsIeBackup.extensionFor("video/mp4"))
    }
}
