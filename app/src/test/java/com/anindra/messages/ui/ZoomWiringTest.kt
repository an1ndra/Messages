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
    fun thePinchIsTakenBeforeTheListCanEatIt() {
        // #348: `Modifier.transformable` lost the gesture to the lazy list's own
        // scroll: its drag consumed the pointers first, which cancels a
        // transform gesture for the whole touch sequence. A real pinch starts
        // with one finger landing before the other, so that was the common case
        // and the chat only zoomed when both fingers landed on the same frame.
        assertTrue(chat.contains("suspend fun PointerInputScope.pinchToZoom()"))
        assertTrue(chat.contains("awaitPointerEvent(PointerEventPass.Initial)"))
        assertTrue(
            "the two-finger changes have to be consumed, or the list scrolls out from under the pinch",
            chat.contains("event.changes.forEach { it.consume() }")
        )
        assertTrue(
            "the transformable modifier is the thing that lost the gesture",
            !chat.contains("rememberTransformableState")
        )
    }

    @Test
    fun thePinchBaselineIsRememberedOutsideTheRestartableHandler() {
        // Compose restarts a pointerInput block when its lambda changes, and this
        // chat recomposes on every zoom step, so the start spread has to live in
        // a remembered object (see ChatZoomTest).
        assertTrue(chat.contains("remember(conversationId) { ChatZoom.Pinch() }"))
        assertTrue(chat.contains("pinch.zoomFor(spread)"))
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

    @Test
    fun thePinchKeepsTheMessageUnderTheFinger() {
        // #348 follow-up: scaling every row changes its height, so without a
        // scroll correction the message the user pinched slides out from under
        // their fingers, which is what made the gesture feel like work.
        assertTrue(
            "the two-finger centroid has to be observed to know what to anchor",
            chat.contains("awaitPointerEvent(PointerEventPass.Initial)")
        )
        assertTrue(chat.contains("ChatZoom.anchorAt(y, listState.zoomItems())"))
        assertTrue(chat.contains("ChatZoom.anchorScroll("))
        assertTrue(chat.contains("listState.scrollBy("))
    }

    @Test
    fun aDoubleTapZoomsWithoutAPinch() {
        // A pinch is two fingers and a careful distance; testers asked for the
        // one-gesture version Google Messages has.
        assertTrue(chat.contains("detectTapGestures("))
        assertTrue(chat.contains("onDoubleTap = { offset ->"))
        assertTrue(chat.contains("ChatZoom.doubleTapTarget(zoom)"))
    }

    @Test
    fun theZoomOutlivesTheChatAndTheProcess() {
        // rememberSaveable alone dies with the process, so the zoom is written
        // through to settings per conversation.
        assertTrue(chat.contains("vm.settings.chatZoomFor(conversationId)"))
        assertTrue(chat.contains("vm.settings.setChatZoomFor(conversationId"))
    }
}
