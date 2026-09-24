package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSupportTest {
    @Test
    fun importsRetrievedInboxAndSentMessagesOnly() {
        assertTrue(MmsSupport.isImportable(1, 132))
        assertTrue(MmsSupport.isImportable(2, 128))
        assertFalse(MmsSupport.isImportable(1, 130))
        assertFalse(MmsSupport.isImportable(3, 128))
        assertFalse(MmsSupport.isImportable(4, 128))
    }

    @Test
    fun detectsAnnouncedButUndownloadedInboxMms() {
        assertTrue(MmsSupport.isPendingDownload(1, MmsSupport.PDU_NOTIFICATION_IND))
        assertFalse(MmsSupport.isPendingDownload(1, MmsSupport.PDU_RETRIEVE_CONF))
        assertFalse(MmsSupport.isPendingDownload(1, MmsSupport.PDU_SEND_REQ))
        assertFalse(MmsSupport.isPendingDownload(2, MmsSupport.PDU_NOTIFICATION_IND))
        assertFalse(MmsSupport.isPendingDownload(3, MmsSupport.PDU_NOTIFICATION_IND))
        assertEquals("msg_box=1 AND m_type=130", MmsSupport.PENDING_DOWNLOAD_SELECTION)
        assertEquals("content://mms/42", MmsSupport.messageContentUri(42))
    }

    @Test
    fun throttlesRepeatedDownloadAttempts() {
        val now = 1_700_000_000_000L
        assertTrue(MmsSupport.shouldRetryDownload(0, now))
        assertTrue(MmsSupport.shouldRetryDownload(-1, now))
        assertFalse(MmsSupport.shouldRetryDownload(now - 1_000, now))
        assertTrue(
            MmsSupport.shouldRetryDownload(now - MmsSupport.DOWNLOAD_RETRY_COOLDOWN_MS, now)
        )
    }

    @Test
    fun resolvesAttachmentMimeWhenTheProviderIsSilent() {
        assertEquals("image/png", MmsSupport.defaultAttachmentMime("image/png", "content://x/a"))
        assertEquals("image/jpeg", MmsSupport.defaultAttachmentMime("image/jpeg; charset=binary", "content://x/a"))
        assertEquals("video/mp4", MmsSupport.defaultAttachmentMime(null, "content://x/clip.MP4"))
        assertEquals("audio/mp4", MmsSupport.defaultAttachmentMime("  ", "content://x/clip.m4a"))
        assertEquals("image/jpeg", MmsSupport.defaultAttachmentMime(null, "content://x/photo"))
    }

    @Test
    fun buildsAttachmentPlusOptionalTextPart() {
        val withCaption = MmsSupport.outgoingParts("image/png", "hello")
        assertEquals(2, withCaption.size)
        assertEquals("image", withCaption[0].name)
        assertEquals("image/png", withCaption[0].mimeType)
        assertFalse(withCaption[0].isText)
        assertEquals("text/plain", withCaption[1].mimeType)
        assertTrue(withCaption[1].isText)
        assertEquals(1, MmsSupport.outgoingParts("image/png", "  ").size)
    }

    @Test
    fun namesSavedAttachmentsSafely() {
        assertEquals(
            "Marek Nowak_1700000000000.jpg",
            MmsSupport.savedAttachmentName("Marek Nowak", 1_700_000_000_000L, "image/jpeg")
        )
        assertEquals(
            "message_1700000000000.png",
            MmsSupport.savedAttachmentName("   ", 1_700_000_000_000L, "image/png")
        )
        assertEquals(
            "a_b_1700000000000.jpg",
            MmsSupport.savedAttachmentName("a/b", 1_700_000_000_000L, "image/jpeg")
        )
        assertEquals(
            "Sara_1700000000000.jpg",
            MmsSupport.savedAttachmentName("Sara", 1_700_000_000_000L, "application/octet-stream")
        )
    }

    @Test
    fun resolvesSavedAttachmentMimeByExtension() {
        assertEquals("image/png", MmsSupport.mimeForSavedAttachment("content://mms/part/9", "image/png"))
        assertEquals("image/png", MmsSupport.mimeForSavedAttachment("content://x/a.PNG", null))
        assertEquals("video/mp4", MmsSupport.mimeForSavedAttachment("content://x/clip.mp4", null))
        assertEquals("image/jpeg", MmsSupport.mimeForSavedAttachment("content://mms/part/9", null))
    }

    @Test
    fun convertsSecondsWithoutOverflow() {
        assertEquals(1_700_000_000_000L, MmsSupport.milliseconds(1_700_000_000L))
        assertNull(MmsSupport.milliseconds(-1))
        assertNull(MmsSupport.milliseconds(Long.MAX_VALUE))
    }

    @Test
    fun resolvesIncomingSenderAndOutgoingRecipient() {
        val incoming = listOf(MmsSupport.Address(137, "+15551234567/TYPE=PLMN"))
        val outgoing = listOf(MmsSupport.Address(137, "insert-address-token"),
            MmsSupport.Address(151, "+15551234567"))
        assertEquals("+15551234567", MmsSupport.peer(1, incoming, 1))
        assertEquals("+15551234567", MmsSupport.peer(2, outgoing, 1))
        assertNull(MmsSupport.peer(1, incoming, 2))
        assertNull(MmsSupport.peer(1, incoming, null))
        assertNull(MmsSupport.phoneAddress("sender@example.test"))
    }

    @Test
    fun combinesTextAndReportsAttachmentsNotDisplayed() {
        val content = MmsSupport.content(listOf(
            MmsSupport.Part(1, "application/smil"),
            MmsSupport.Part(2, "text/plain", "Caption"),
            MmsSupport.Part(3, "IMAGE/JPEG; name=photo.jpg"),
            MmsSupport.Part(4, "image/png"),
            MmsSupport.Part(5, "video/mp4")
        ))
        assertEquals(3L, content.imageId)
        assertEquals(2, content.omittedParts)
        assertTrue(content.body.startsWith("Caption\n"))
        assertTrue(content.body.contains("2 additional/unsupported"))
    }

    @Test
    fun boundsStreamedTextAndDecodesCharset() {
        assertTrue(MmsSupport.acceptsTextChunk(0, MmsSupport.MAX_TEXT_BYTES))
        assertFalse(MmsSupport.acceptsTextChunk(MmsSupport.MAX_TEXT_BYTES, 1))
        assertFalse(MmsSupport.acceptsTextChunk(-1, 1))
        assertEquals("café", MmsSupport.decodeText(byteArrayOf(99, 97, 102, -23), 4))
    }

    @Test
    fun distinguishesProviderNamespaces() {
        assertEquals("content://sms", MmsSupport.providerUri(MmsSupport.TRANSPORT_SMS))
        assertEquals("content://mms", MmsSupport.providerUri(MmsSupport.TRANSPORT_MMS))
        assertNull(MmsSupport.providerUri("unknown"))
    }
}
