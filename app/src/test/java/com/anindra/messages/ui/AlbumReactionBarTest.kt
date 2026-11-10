package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The long-press reaction bar has to appear on a multi-picture bubble (#364 M6).
 *
 * A multi-picture MMS is stored as one row per picture and drawn as one bubble;
 * its long-press selects every row. The bar was gated on the bubble being a
 * single row, so an album could never carry a reaction at all. The gate has to
 * stay "this bubble is the whole selection" though: selecting a second message
 * still hides the bar (5c7e694), because a reaction acts on one message.
 */
class AlbumReactionBarTest {

    private val screen: String by lazy {
        val relative = "app/src/main/java/com/anindra/messages/ui/ChatScreen.kt"
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return@lazy candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate $relative")
    }

    @Test
    fun aSingleMessageNeedsToBeTheOnlySelection() {
        assertTrue(bubbleIsTheWholeSelection(listOf(7L), setOf(7L)))
        assertFalse(bubbleIsTheWholeSelection(listOf(7L), emptySet()))
        assertFalse(bubbleIsTheWholeSelection(listOf(7L), setOf(8L)))
        assertFalse(
            "selecting a second message must hide the bar",
            bubbleIsTheWholeSelection(listOf(7L), setOf(7L, 9L)),
        )
    }

    @Test
    fun anAlbumNeedsEveryPictureSelectedAndNothingElse() {
        val album = listOf(11L, 12L, 13L)
        assertTrue(bubbleIsTheWholeSelection(album, setOf(11L, 12L, 13L)))
        assertFalse(
            "a partially selected album must not show the bar",
            bubbleIsTheWholeSelection(album, setOf(11L, 12L)),
        )
        assertFalse(
            "one row of the album is not the album",
            bubbleIsTheWholeSelection(album, setOf(11L)),
        )
        assertFalse(
            "an album plus another message is not one message",
            bubbleIsTheWholeSelection(album, setOf(11L, 12L, 13L, 14L)),
        )
    }

    @Test
    fun anEmptyBubbleNeverShowsTheBar() {
        assertFalse(bubbleIsTheWholeSelection(emptyList(), setOf(1L)))
    }

    @Test
    fun theBubbleGatesTheBarOnItsWholeSelection() {
        assertTrue(
            "the bar must ask whether the whole bubble is selected",
            screen.contains("bubbleIsTheWholeSelection(rowIds, selectedMessageIds)"),
        )
        assertFalse(
            "the single-row gate is what kept reactions off albums",
            screen.contains("rowIds.size == 1"),
        )
    }
}
