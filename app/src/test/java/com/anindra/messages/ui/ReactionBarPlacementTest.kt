package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reaction bar floats over the bubble and hugs the same physical edge the
 * bubble sits on. The old placement keyed only on `isMe`, so in RTL the bar was
 * drawn past the opposite edge of the screen and its first emoji fell outside
 * the window (#408).
 */
class ReactionBarPlacementTest {

    private val bubble = 600
    private val bar = 220

    @Test
    fun sentBubbleInLtrHugsThePhysicalRight() {
        assertEquals(bubble - bar, reactionBarX(isMe = true, isRtl = false, bubble, bar))
    }

    @Test
    fun receivedBubbleInLtrHugsThePhysicalLeft() {
        assertEquals(0, reactionBarX(isMe = false, isRtl = false, bubble, bar))
    }

    @Test
    fun sentBubbleInRtlHugsThePhysicalLeft() {
        // Sent messages mirror to the left side, so the bar must follow them.
        assertEquals(0, reactionBarX(isMe = true, isRtl = true, bubble, bar))
    }

    @Test
    fun receivedBubbleInRtlHugsThePhysicalRight() {
        assertEquals(bubble - bar, reactionBarX(isMe = false, isRtl = true, bubble, bar))
    }

    @Test
    fun theBarNeverOverhangsTheBubble() {
        listOf(false, true).forEach { rtl ->
            listOf(false, true).forEach { mine ->
                val x = reactionBarX(mine, rtl, bubble, bar)
                assertEquals(
                    "isMe=$mine isRtl=$rtl must sit at one of the bubble's edges",
                    true,
                    x == 0 || x == bubble - bar
                )
            }
        }
    }

    @Test
    fun mirroringIsTheOnlyDifferenceBetweenLtrAndRtl() {
        assertEquals(
            reactionBarX(isMe = true, isRtl = false, bubble, bar),
            reactionBarX(isMe = false, isRtl = true, bubble, bar)
        )
        assertEquals(
            reactionBarX(isMe = false, isRtl = false, bubble, bar),
            reactionBarX(isMe = true, isRtl = true, bubble, bar)
        )
    }
}
