package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScrollFollowGuardTest {

    private val chatScreenSrc: String = run {
        val resource = javaClass.classLoader.getResource("com/anindra/messages/ui/ChatScreen.kt")
        if (resource != null) {
            File(resource.file).readText()
        } else {
            val candidates = listOf(
                "app/src/main/java/com/anindra/messages/ui/ChatScreen.kt",
                "../app/src/main/java/com/anindra/messages/ui/ChatScreen.kt",
                "../../app/src/main/java/com/anindra/messages/ui/ChatScreen.kt"
            )
            candidates.firstNotNullOfOrNull { path ->
                File(path).takeIf { it.exists() }?.readText()
            } ?: error("ChatScreen.kt not found")
        }
    }

    /** The follow block, from its state to the effect after it. Sliced by
     *  landmarks rather than a fixed length: the region grows with the comments
     *  that explain why it is shaped this way. */
    private fun followEffect(): String {
        val start = chatScreenSrc.indexOf("var userDraggedList by remember(conversationId)")
        require(start >= 0) { "scroll-follow effect not found in ChatScreen.kt" }
        val end = chatScreenSrc.indexOf("val chatLifecycleOwner", start)
        require(end > start) { "end of scroll-follow effect not found" }
        return chatScreenSrc.substring(start, end)
    }

    @Test
    fun scrollFollowReArmsOnEachNewMessage() {
        assertTrue(
            "follow window must be keyed on newestId, else a picture arriving " +
                "after the window expired grows with nothing following it",
            followEffect().contains("LaunchedEffect(conversationId, newestId, focusedSearchId)")
        )
    }

    @Test
    fun scrollFollowDoesNotGuardOnLastVisibleIndex() {
        assertFalse(chatScreenSrc.contains("lastVisible >= info.totalItemsCount - 2"))
    }

    @Test
    fun scrollFollowStillRespectsUserDrag() {
        assertTrue(followEffect().contains("!userDraggedList"))
    }

    @Test
    fun scrollFollowStillScrollsToBottom() {
        assertTrue(followEffect().contains("listState.scrollToItem(Int.MAX_VALUE)"))
    }

    @Test
    fun scrollFollowHasExactlyOneEffect() {
        assertEquals(
            "the follow logic must live in one place, not two duplicated collectors",
            1,
            Regex("userDraggedList by remember\\(conversationId\\)").findAll(chatScreenSrc).count()
        )
    }
}
