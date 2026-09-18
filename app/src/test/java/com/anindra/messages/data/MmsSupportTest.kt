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
