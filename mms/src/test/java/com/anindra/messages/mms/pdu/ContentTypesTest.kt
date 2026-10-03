package com.anindra.messages.mms.pdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two tables every other layer reads. Their index positions are wire format,
 * so a test that only round-tripped would pass even after a row was inserted or
 * dropped — these assert the pinned positions instead.
 */
class ContentTypesTest {

    @Test
    fun lastKnownCodeIsMikey() {
        assertEquals("application/mikey", ContentTypes.at(0x52))
        assertEquals(82, ContentTypes.TABLE.size - 1)
    }

    @Test
    fun firstKnownCodeIsTheWildcard() {
        assertEquals(ContentTypes.WILDCARD, ContentTypes.at(0x00))
    }

    @Test
    fun anIndexPastTheTableDecodesToTheWildcardRatherThanFailing() {
        assertEquals(ContentTypes.WILDCARD, ContentTypes.at(0x53))
        assertEquals(ContentTypes.WILDCARD, ContentTypes.at(0x7F))
        assertEquals(ContentTypes.WILDCARD, ContentTypes.at(0xFFFF))
    }

    @Test
    fun multipartRelatedIsAConstrainedCode() {
        assertEquals(0x33, ContentTypes.indexOf(ContentTypes.MULTIPART_RELATED))
    }

    @Test
    fun indexLookupIgnoresCaseAndParameters() {
        assertEquals(0x1E, ContentTypes.indexOf("IMAGE/JPEG"))
        assertEquals(0x1E, ContentTypes.indexOf("image/jpeg; name=photo.jpg"))
        assertEquals(0x1E, ContentTypes.indexOf("  image/jpeg  "))
    }

    @Test
    fun anUnlistedTypeHasNoCodeAndSoIsWrittenAsText() {
        assertNull(ContentTypes.indexOf("image/heic"))
        assertNotNull(ContentTypes.indexOf("text/plain"))
    }

    @Test
    fun theWriterEmitsAConstrainedCodeOnlyForAListedType() {
        val listed = WspWriter().apply { appendConstrainedMedia("image/jpeg") }.toByteArray()
        assertEquals(1, listed.size)
        assertEquals(0x80 or 0x1E, listed[0].toInt() and 0xFF)

        val unlisted = WspWriter().apply { appendConstrainedMedia("image/heic") }.toByteArray()
        assertTrue(unlisted.size > 2)
        assertEquals("image/heic", WspReader(unlisted).readConstrainedMedia())
    }

    @Test
    fun aConstrainedCodeIsReadBackWithoutTouchingTheStream() {
        // 0x80 or 0x51 = index 81 = application/vnd.oma.dd2+xml, then 0xB3.
        val reader = WspReader(byteArrayOf(0xD1.toByte(), 0xB3.toByte(), 0x00))
        assertEquals("application/vnd.oma.dd2+xml", reader.readConstrainedMedia())
        // The next octet is still there, so the reader consumed exactly one.
        assertEquals(0xB3, reader.readOctet())
    }

    @Test
    fun familyHelpersClassifyByNormalizedType() {
        assertTrue(ContentTypes.isImage("image/png"))
        assertTrue(ContentTypes.isText("text/x-vCard"))
        assertTrue(ContentTypes.isAudio("audio/amr"))
        assertTrue(ContentTypes.isVideo("video/mp4"))
        assertTrue(ContentTypes.isMultipart(ContentTypes.MULTIPART_RELATED))
        assertTrue(ContentTypes.isMultipart("multipart/alternative"))
        assertTrue(!ContentTypes.isMultipart("text/plain"))
    }
}
