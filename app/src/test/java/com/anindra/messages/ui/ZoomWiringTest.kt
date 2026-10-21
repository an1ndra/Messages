package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #348: the pinch gesture must be wired to the chat's font scale, not to a
 * graphics-layer zoom. A layer scale would keep the same layout and only stretch
 * the pixels, so the bubbles would not re-fit and the script's height assertion
 * would stay flat.
 */
class ZoomWiringTest {

    private val chat: String by lazy {
        val main = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
        File(main, "java/com/anindra/messages/ui/ChatScreen.kt").readText()
    }

    @Test
    fun theMessageListCarriesATransformableGesture() {
        assertTrue(chat.contains("rememberTransformableState"))
        assertTrue(chat.contains(".transformable(zoomState)"))
    }

    @Test
    fun theZoomDrivesTheFontScaleRatherThanALayerScale() {
        assertTrue(
            "the list must be recomposed at the zoomed font scale",
            chat.contains("LocalDensity provides zoomedDensity")
        )
        assertTrue(
            "the zoomed density has to be derived from the pinch value",
            chat.contains("baseDensity.fontScale * ChatZoom.fontScale(zoom)")
        )
        assertTrue(
            "a graphics-layer scale would only stretch pixels",
            !chat.contains("scaleX = zoom")
        )
    }

    @Test
    fun theZoomIsRememberedPerConversation() {
        assertTrue(chat.contains("rememberSaveable(conversationId)"))
    }

    @Test
    fun theCurrentZoomIsShownAndCanBeResetInOneTap() {
        // Without this the only way back to 100% was pinching the other way.
        assertTrue(chat.contains("ChatZoom.isDefault(zoom)"))
        assertTrue(chat.contains("onClick = { zoom = ChatZoom.DEFAULT }"))
        assertTrue(chat.contains("R.string.access_zoom_reset"))
    }
}
