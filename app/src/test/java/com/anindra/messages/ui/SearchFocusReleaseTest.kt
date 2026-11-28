package com.anindra.messages.ui

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFocusReleaseTest {
    private class RecordingFocusManager : FocusManager {
        val events = mutableListOf<String>()
        var forced = false

        override fun clearFocus(force: Boolean) {
            forced = force
            events += "clear"
        }

        override fun moveFocus(focusDirection: FocusDirection): Boolean {
            events += "move"
            return false
        }
    }

    private class RecordingKeyboard : SoftwareKeyboardController {
        val events = mutableListOf<String>()

        override fun show() { events += "show" }

        override fun hide() { events += "hide" }
    }

    @Test
    fun openingAResultClearsFocusHidesTheKeyboardThenForwardsTheQuery() {
        val focus = RecordingFocusManager()
        val keyboard = RecordingKeyboard()
        val events = mutableListOf<String>()

        SearchFocusRelease(focus, keyboard) { id, query ->
            events += "open:$id:$query"
        }(42L, "hello")

        assertTrue(focus.forced)
        assertEquals(listOf("clear"), focus.events)
        assertEquals(listOf("hide"), keyboard.events)
        assertEquals(
            listOf("clear", "hide", "open:42:hello"),
            focus.events + keyboard.events + events
        )
    }

    @Test
    fun openingAResultStillForwardsWhenThereIsNoKeyboardController() {
        val focus = RecordingFocusManager()
        var opened: Pair<Long, String>? = null

        SearchFocusRelease(focus, null) { id, query -> opened = id to query }(7L, "pizza")

        assertEquals(listOf("clear"), focus.events)
        assertEquals(7L to "pizza", opened)
    }

    @Test
    fun bothConversationListsRouteResultTapsThroughTheFocusRelease() {
        val newList = source("ui/ConversationsScreen.kt")
        val legacyList = source("ui/legacy/LegacyConversationsScreen.kt")

        listOf(newList, legacyList).forEach { list ->
            assertTrue("the list must construct the focus release", list.contains("SearchFocusRelease("))
            assertTrue(
                "a result tap must clear focus before the callback",
                list.contains("openConversationFromSearch("),
            )
        }
        assertTrue(
            "starred hits are results too",
            newList.contains("openConversationFromSearch(hit.conversationId, hit.body)"),
        )
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/com/anindra/messages/$relative")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate $relative")
    }
}
