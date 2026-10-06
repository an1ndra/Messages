package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A lone bubble and the first of a run carry the "tail": only the bottom
 * corner on the sender's stack side — start (left) for received bubbles, end
 * (right) for sent ones — is flat. Inside a run the flat corner sits
 * wherever a bubble touches a neighbour, so a stack reads as one connected
 * block.
 */
class BubbleCornerShapeTest {

    private val round = 18f
    private val flat = 4f

    @Test
    fun aLoneReceivedBubbleCarriesTheTailOnItsBottomLeftCorner() {
        assertEquals(
            BubbleCorners(round, round, flat, round),
            bubbleCorners(BubblePosition.SINGLE, isMe = false)
        )
    }

    @Test
    fun aLoneSentBubbleCarriesTheTailOnItsBottomRightCorner() {
        assertEquals(
            BubbleCorners(round, round, round, flat),
            bubbleCorners(BubblePosition.SINGLE, isMe = true)
        )
    }

    @Test
    fun firstAndLastFlattenOnlyTheCornerJoinedToTheRun() {
        // FIRST continues downward: its bottom corner is the flat one.
        assertEquals(
            BubbleCorners(round, round, flat, round),
            bubbleCorners(BubblePosition.FIRST, isMe = false)
        )
        assertEquals(
            BubbleCorners(round, round, round, flat),
            bubbleCorners(BubblePosition.FIRST, isMe = true)
        )
        // LAST continues from above: its top corner is the flat one.
        assertEquals(
            BubbleCorners(flat, round, round, round),
            bubbleCorners(BubblePosition.LAST, isMe = false)
        )
        assertEquals(
            BubbleCorners(round, flat, round, round),
            bubbleCorners(BubblePosition.LAST, isMe = true)
        )
    }

    @Test
    fun aMiddleBubbleIsFlatOnTheJoinedSide() {
        assertEquals(
            BubbleCorners(flat, round, flat, round),
            bubbleCorners(BubblePosition.MIDDLE, isMe = false)
        )
        assertEquals(
            BubbleCorners(round, flat, round, flat),
            bubbleCorners(BubblePosition.MIDDLE, isMe = true)
        )
    }

    @Test
    fun theOuterCornersAreNeverRounderThanTheInnerOnes() {
        for (pos in BubblePosition.entries) {
            for (mine in listOf(true, false)) {
                val c = bubbleCorners(pos, mine)
                val outer = if (mine) listOf(c.topEnd, c.bottomEnd) else listOf(c.topStart, c.bottomStart)
                val inner = if (mine) listOf(c.topStart, c.bottomStart) else listOf(c.topEnd, c.bottomEnd)
                assertTrue(
                    "outer corners must not exceed the inner ones ($pos, mine=$mine): $c",
                    outer.all { it <= inner.min() }
                )
            }
        }
    }
}