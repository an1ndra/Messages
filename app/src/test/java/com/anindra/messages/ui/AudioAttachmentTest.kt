package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Issue #481: an audio file can be attached, not only a voice note.
 *
 * The transport already handled `audio` — a voice note is stored that way — so
 * what was missing was the way in: a row in the attach sheet and a picker that
 * can reach music and recordings. The photo picker cannot, so the entry has to
 * go through the document picker.
 */
class AudioAttachmentTest {

    private val chat: String by lazy {
        val candidates = listOf(
            "app/src/main/java/com/anindra/messages/ui/ChatScreen.kt",
            "../app/src/main/java/com/anindra/messages/ui/ChatScreen.kt",
            "../../app/src/main/java/com/anindra/messages/ui/ChatScreen.kt"
        )
        candidates.firstNotNullOfOrNull { File(it).takeIf { f -> f.exists() }?.readText() }
            ?: error("ChatScreen.kt not found")
    }

    @Test
    fun theAttachSheetOffersAudio() {
        val sheet = chat.substringAfter("internal fun AttachSheet(").substringBefore("@Composable")
        assertTrue("the sheet must take an onAudio callback", sheet.contains("onAudio: () -> Unit"))
        assertTrue("and draw a row for it", sheet.contains("R.string.chat_media_audio"))
    }

    @Test
    fun audioIsPickedThroughTheDocumentPicker() {
        assertTrue(
            "the photo picker cannot return audio; the document picker can",
            chat.contains("ActivityResultContracts.OpenDocument()")
        )
        assertTrue(
            "the picker has to be filtered to audio",
            chat.contains("""audioLauncher.launch(arrayOf("audio/*"))""")
        )
    }

    @Test
    fun thePickedFileJoinsTheComposerTray() {
        val launcher = chat.substringAfter("val audioLauncher").substringBefore("fun leaveChat")
        assertTrue("the uri is added to the pending media tray", launcher.contains("pendingMedia.add(uri)"))
    }
}
