package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A lone bubble and the first of a run carry the "tail": only the bottom
 * corner on the sender's stack side — start (left) for received bubbles, end
 * (right) for sent ones — is flat. Inside a run the reduced radius sits
 * wherever a bubble touches a neighbour, so a stack reads as one connected
 * block.
 */
class BubbleCornerShapeTest {

    private val round = 18f
    private val flat = 4f
    private val joined = 8f

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
    fun aMiddleBubbleIsSoftenedOnTheJoinedSide() {
        assertEquals(
            BubbleCorners(joined, round, joined, round),
            bubbleCorners(BubblePosition.MIDDLE, isMe = false)
        )
        assertEquals(
            BubbleCorners(round, joined, round, joined),
            bubbleCorners(BubblePosition.MIDDLE, isMe = true)
        )
    }

    @Test
    fun theJoinIsRounderThanTheTail() {
        // These were one constant because both were 4dp. Guard the split: if
        // they are merged again the middle bubble silently squares off.
        val middle = bubbleCorners(BubblePosition.MIDDLE, isMe = true)
        val tail = bubbleCorners(BubblePosition.SINGLE, isMe = true)
        assertTrue(
            "join ${middle.topEnd} must be rounder than the tail ${tail.bottomEnd}",
            middle.topEnd > tail.bottomEnd
        )
        assertEquals(middle.topEnd, middle.bottomEnd, 0f)
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