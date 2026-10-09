package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tapping an image bubble must open a full-screen preview of it.
 *
 * The tap used to be wired to the bubble's generic `onTap`, which only toggles
 * link reveal — an image has no links, so the tap did nothing observable and
 * the picture was stuck at thumbnail size forever. This pins the wiring, since
 * neither the tap target nor the dismissal is JVM-constructible.
 */
class ImagePreviewWiringTest {
    private val chatSource: String by lazy {
        File("src/main/java/com/anindra/messages/ui/ChatScreen.kt").readText()
    }

    private fun bodyOf(function: String): String {
        val start = chatSource.indexOf("fun $function(")
        assertTrue("could not find $function in ChatScreen.kt", start >= 0)
        // Cut at the next top-level `fun ` so a later function cannot satisfy
        // an assertion that belonged to this one.
        val next = chatSource.indexOf("\nfun ", start)
        return if (next < 0) chatSource.substring(start) else chatSource.substring(start, next)
    }

    @Test
    fun imageBubbleTapOpensThePreviewRatherThanTheGenericTap() {
        val bubble = bodyOf("ChatBubble")
        val imageBranch = bubble.substringAfter("if (msg.mediaType == \"image\"")
        assertTrue(
            "the image branch must route its tap to onImageTap",
            imageBranch.contains("onClick = { onImageTap(msg.mediaUri) }"),
        )
    }

    @Test
    fun chatScreenKeepsTheUriItWasTappedWith() {
        assertTrue(
            "ChatScreen must hold the previewed URI in state",
            chatSource.contains("var previewImageUri by remember { mutableStateOf<String?>(null) }"),
        )
        assertTrue(
            "the tapped URI must be what is stored",
            chatSource.contains("previewImageUri = uri"),
        )
        assertTrue(
            "the preview must be rendered from that state",
            chatSource.contains("previewImageUri?.let { uri ->"),
        )
    }

    @Test
    fun previewFitsTheWholeFrameRatherThanCropping() {
        val preview = bodyOf("ImagePreview")
        assertTrue(
            "the preview must use ContentScale.Fit — Crop would hide what it cannot show",
            preview.contains("contentScale = ContentScale.Fit"),
        )
    }

    @Test
    fun backClosesThePreviewInsteadOfLeavingTheChat() {
        val handlers = chatSource.substringAfter("BackHandler(enabled = previewImageUri != null)")
        assertTrue(
            "back must dismiss the preview",
            handlers.substringBefore("\n").contains("previewImageUri = null"),
        )
        assertTrue(
            "the preview's own BackHandler must dismiss it too",
            bodyOf("ImagePreview").contains("BackHandler(onBack = onDismiss)"),
        )
    }
}