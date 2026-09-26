package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Attachments used to be stored as "image" unconditionally, so a video MMS was
 * transmitted correctly but rendered as a Photo in the thread and conversation
 * list (Repository labels rows by `media_type`).
 */
class AttachmentTypeTest {

    private fun typeOf(mime: String?, uri: String): String {
        val resolved = MmsSupport.defaultAttachmentMime(mime, uri)
        return when {
            resolved.startsWith("audio/") -> "audio"
            resolved.startsWith("video/") -> "video"
            else -> "image"
        }
    }

    @Test
    fun imagesStayImages() {
        assertEquals("image", typeOf("image/png", "content://x/photo.png"))
        assertEquals("image", typeOf("image/jpeg", "content://x/photo.jpg"))
        assertEquals("image", typeOf("image/webp", "content://x/photo.webp"))
    }

    @Test
    fun videosAreNotLabelledPhoto() {
        assertEquals("video", typeOf("video/mp4", "content://x/clip.mp4"))
        assertEquals("video", typeOf("video/3gpp", "content://x/clip.3gp"))
        assertEquals("video", typeOf(null, "content://x/clip.mp4"))
        assertEquals("video", typeOf(null, "content://x/clip.webm"))
    }

    @Test
    fun audioIsNotLabelledPhoto() {
        assertEquals("audio", typeOf("audio/mp4", "content://x/clip.m4a"))
        assertEquals("audio", typeOf(null, "content://x/clip.mp3"))
        assertEquals("audio", typeOf(null, "content://x/clip.ogg"))
    }

    @Test
    fun unknownTypesFallBackToImage() {
        assertEquals("image", typeOf(null, "content://x/thing"))
        assertEquals("image", typeOf("application/octet-stream", "content://x/thing.bin"))
    }
}
