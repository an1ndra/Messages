package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Picking a picture from '+' -> Gallery sent it the instant the picker closed,
 * so it could not be captioned or dropped (#326). The picture now waits in the
 * composer until Send, and the send button lights up for it.
 */
class PendingMediaWiringTest {
    private val chat: String by lazy { locate("ui/ChatScreen.kt") }

    @Test
    fun pickingMediaStagesItInsteadOfSending() {
        assertTrue(
            "a captured photo must be staged, not sent",
            chat.contains("cameraFileUri?.let { pendingMedia.add(it) }"),
        )
        assertTrue(
            "a gallery pick must be staged, not sent",
            chat.contains("uris.filter { it !in pendingMedia }.forEach { pendingMedia.add(it) }"),
        )
        assertFalse(
            "picking a picture must not send it immediately",
            chat.contains("vm.sendMediaMessage(conversationId, it)"),
        )
    }

    @Test
    fun theGalleryPickersAllowSeveralPhotosInOnePass() {
        assertTrue(
            "the gallery must use the multi-select picker",
            chat.contains("ActivityResultContracts.PickMultipleVisualMedia()"),
        )
        assertFalse(
            "the single-select gallery picker must be gone",
            chat.contains("ActivityResultContracts.PickVisualMedia()"),
        )
    }

    @Test
    fun theComposerOffersSendWhenAPictureIsWaiting() {
        assertTrue(
            "the composer must be told an attachment is waiting",
            chat.contains("hasAttachments = pendingMedia.isNotEmpty()"),
        )
        assertTrue(
            "the send button must be live for an attachment with no text",
            chat.contains("val ready = canSend(draft, hasAttachments)"),
        )
    }

    @Test
    fun sendPublishesTheStagedPictures() {
        assertTrue(
            "there must be a function that sends the staged media",
            chat.contains("fun sendPendingMedia()"),
        )
        assertTrue(
            "the draft must ride as the first picture's caption",
            chat.contains("vm.sendMediaMessage(conversationId, uri, if (index == 0) caption else \"\")"),
        )
    }

    @Test
    fun eachPictureCanBeRemovedBeforeSending() {
        assertTrue(
            "each staged picture must offer a remove control",
            Regex("pendingMedia\\.forEach[\\s\\S]{0,1200}?pendingMedia\\.remove\\(uri\\)").containsMatchIn(chat),
        )
    }

    private fun locate(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/com/anindra/messages/$relative")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate $relative")
    }
}
