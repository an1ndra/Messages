package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertEquals
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
        // Any top-level declaration ends the body, whatever its visibility:
        // cutting only on "\nfun " ran this test's assertions into the next
        // `internal fun` and counted its inset paddings as this composable's.
        val next = Regex("\\n(?:@\\w+|(?:internal |private |public )?fun )")
            .find(chatSource, start + 1)?.range?.first ?: -1
        return if (next < 0) chatSource.substring(start) else chatSource.substring(start, next)
    }

    @Test
    fun imageBubbleTapOpensThePreviewRatherThanTheGenericTap() {
        val bubble = bodyOf("ChatBubble")
        val imageBranch = bubble.substringAfter("if (msg.mediaType == \"image\"")
        assertTrue(
            "the image branch must route its tap to onImageTap",
            imageBranch.contains("onClick = { onImageTap(msg) }"),
        )
    }

    @Test
    fun chatScreenKeepsTheTappedRow() {
        // The row, not just the URI: Save goes through the message id, which is
        // also what tells two pictures of one album apart (#398, #460).
        assertTrue(
            "ChatScreen must hold the previewed message in state",
            chatSource.contains("var previewImage by remember { mutableStateOf<Message?>(null) }"),
        )
        assertTrue(
            "the tapped message must be what is stored",
            chatSource.contains("previewImage = picture"),
        )
        assertTrue(
            "the preview must be rendered from that state",
            chatSource.contains("previewImage?.let { picture ->"),
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

    /**
     * The close button has to clear the status bar.
     *
     * `align(TopEnd)` put it at y=21..147 on this AVD, and the status-bar inset
     * covers everything above y=137 (52dp at 420dpi). The button rendered, was
     * present in the accessibility tree with a clickable node, and did nothing
     * when tapped — only back and a tap below the inset worked.
     */
    @Test
    fun closeButtonAndBackdropClearTheStatusBarInset() {
        val preview = bodyOf("ImagePreview")
        assertTrue(
            "the close button must be padded clear of the status bar",
            preview.contains(".windowInsetsPadding(WindowInsets.statusBars)"),
        )
        assertEquals(
            "both the backdrop and the button need the inset padding",
            2,
            Regex("\\.windowInsetsPadding\\(WindowInsets\\.statusBars\\)").findAll(preview).count(),
        )
    }

    @Test
    fun closeButtonDismissesThePreview() {
        val preview = bodyOf("ImagePreview")
        val button = preview.substringAfter("contentAlignment = Alignment.Center")
        assertTrue(
            "the close button must call onDismiss",
            button.contains("onClick = onDismiss"),
        )
    }

    @Test
    fun theImageViewerZoomsWithAPinchAndADoubleTap() {
        val preview = bodyOf("ImagePreview")
        assertTrue(
            "a pinch must drive the viewer's scale",
            preview.contains("detectTransformGestures"),
        )
        assertTrue(
            "a double tap must jump between fit and the zoom step",
            preview.contains("onDoubleTap =") &&
                preview.contains("ViewerZoom.doubleTapTarget(scale)"),
        )
        assertTrue(
            "the transform must be a graphics layer, not a re-layout",
            preview.contains("graphicsLayer {") && preview.contains("scaleX = scale"),
        )
        assertTrue(
            "panning must be clamped so the picture cannot leave its frame",
            preview.contains("ViewerZoom.clampPan"),
        )
    }

    @Test
    fun theViewerCanSaveThePictureItShows() {
        val preview = bodyOf("ImagePreview")
        assertTrue(
            "the viewer must offer a save control",
            preview.contains("onSave") && preview.contains("Icons.Rounded.Download"),
        )
        // A save control with no handler behind it is the dead control this
        // project forbids; the chat has to wire it to the real save path.
        assertTrue(
            "the save control must reach the message save path",
            chatSource.contains("onSave = { vm.saveMessageImage(picture.id) }"),
        )
    }

    @Test
    fun backClosesThePreviewInsteadOfLeavingTheChat() {
        val handlers = chatSource.substringAfter("BackHandler(enabled = previewImage != null)")
        assertTrue(
            "back must dismiss the preview",
            handlers.substringBefore("\n").contains("previewImage = null"),
        )
        assertTrue(
            "the preview's own BackHandler must dismiss it too",
            bodyOf("ImagePreview").contains("BackHandler(onBack = onDismiss)"),
        )
    }
}