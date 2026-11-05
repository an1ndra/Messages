package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reported: "When click back button or swap back in phone Close chat screen not
 * showing animation but opening chat screen showing animation."
 *
 * The asymmetry was real. Opening slid the chat in solid — the fade was removed
 * there because the list showed through the bubbles — while closing faded the
 * chat out over 200ms *while* sliding it, so it dissolved into the list instead
 * of moving. Same rule on both sides now: the chat is one opaque surface that
 * carries the whole move, and the list behind it needs no enter transition.
 */
class ChatTransitionTest {

    private val src: String by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/anindra/messages/MainActivity.kt") }
            .first { it.isFile }
            .readText()
    }

    /** The `when` branch that runs when a chat closes back to the list. */
    private fun closeBranch(): String {
        val start = src.indexOf("targetState == \"list\" && initialState == \"chat\" ->")
        require(start >= 0) { "the chat->list branch is missing" }
        return src.substring(start, src.indexOf("to > from ->", start))
    }

    /** Same for opening a chat from the list. */
    private fun openBranch(): String {
        val start = src.indexOf("targetState == \"chat\" && initialState == \"list\" ->")
        require(start >= 0) { "the list->chat branch is missing" }
        return src.substring(start, src.indexOf("targetState == \"list\"", start))
    }

    @Test
    fun closingSlidesTheChatAndDoesNotFadeIt() {
        val branch = closeBranch()
        assertTrue(
            "closing must slide the chat down",
            branch.contains("slideOutVertically(navSlide) { it }")
        )
        assertFalse(
            "fading the outgoing chat let the list show through it while it slid",
            branch.contains("fadeOut")
        )
    }

    @Test
    fun theEnteringListAddsNothing() {
        assertTrue(
            "the list is drawn behind the container, so it needs no enter transition",
            closeBranch().contains("EnterTransition.None")
        )
    }

    @Test
    fun openingStaysSolidAndRises() {
        val branch = openBranch()
        assertTrue(branch.contains("slideInVertically(navSlide) { it }"))
        assertFalse(
            "the incoming chat must not fade in over the list",
            branch.contains("fadeIn")
        )
    }

    @Test
    fun theClosePathIsMarkedForTheDeviceScript() {
        assertTrue(src.contains("\"NavTransition\""))
    }
}
