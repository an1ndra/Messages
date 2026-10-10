package com.anindra.messages.ui

import com.anindra.messages.data.Message
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageGroupingTest {

    private fun msg(id: Long, isMe: Boolean, timestamp: Long = 1_700_000_000_000L + id * 1_000L) =
        Message(
            id = id,
            conversationId = 1L,
            body = "m$id",
            timestamp = timestamp,
            isMe = isMe,
            status = "SMS"
        )

    private fun positions(vararg senders: Boolean): List<BubblePosition> {
        val list = senders.mapIndexed { i, me -> msg(i.toLong() + 1, me) }
        return list.indices.map { bubblePosition(list, it) }
    }

    @Test
    fun loneMessageIsSingle() {
        assertEquals(listOf(BubblePosition.SINGLE), positions(true))
        assertEquals(listOf(BubblePosition.SINGLE), positions(false))
    }

    @Test
    fun twoMessageRunIsFirstThenLast() {
        assertEquals(
            listOf(BubblePosition.FIRST, BubblePosition.LAST),
            positions(true, true)
        )
        assertEquals(
            listOf(BubblePosition.FIRST, BubblePosition.LAST),
            positions(false, false)
        )
    }

    @Test
    fun middleMessagesOfALongRunAreFullyRounded() {
        assertEquals(
            listOf(BubblePosition.FIRST, BubblePosition.MIDDLE, BubblePosition.LAST),
            positions(true, true, true)
        )
        assertEquals(
            listOf(
                BubblePosition.FIRST,
                BubblePosition.MIDDLE,
                BubblePosition.MIDDLE,
                BubblePosition.LAST
            ),
            positions(false, false, false, false)
        )
    }

    @Test
    fun alternatingSendersBreakTheRun() {
        assertEquals(
            listOf(BubblePosition.SINGLE, BubblePosition.SINGLE, BubblePosition.SINGLE),
            positions(true, false, true)
        )
    }

    @Test
    fun aTimeGapBreaksTheRun() {
        val first = msg(1, isMe = true)
        val second = msg(2, isMe = true, timestamp = first.timestamp + GROUP_GAP_MS + 1)
        val list = listOf(first, second)
        assertEquals(BubblePosition.SINGLE, bubblePosition(list, 0))
        assertEquals(BubblePosition.SINGLE, bubblePosition(list, 1))
    }

    @Test
    fun outgoingSingleCarriesTheTailOnTheBottomRight() {
        // A lone sent bubble is flat only on its bottom-right corner.
        val single = bubbleCorners(BubblePosition.SINGLE, isMe = true)
        assertEquals(18f, single.topStart, 0f)
        assertEquals(18f, single.topEnd, 0f)
        assertEquals(18f, single.bottomStart, 0f)
        assertEquals(4f, single.bottomEnd, 0f)
    }

    @Test
    fun outgoingFirstAndLastFlattenOnlyTheJoinedCorner() {
        // FIRST continues downward, so its bottom-right corner is the flat one.
        val first = bubbleCorners(BubblePosition.FIRST, isMe = true)
        assertEquals(18f, first.topStart, 0f)
        assertEquals(18f, first.topEnd, 0f)
        assertEquals(18f, first.bottomStart, 0f)
        assertEquals(4f, first.bottomEnd, 0f)

        // LAST continues from above, so its top-right corner is the flat one.
        val last = bubbleCorners(BubblePosition.LAST, isMe = true)
        assertEquals(18f, last.topStart, 0f)
        assertEquals(4f, last.topEnd, 0f)
        assertEquals(18f, last.bottomStart, 0f)
        assertEquals(18f, last.bottomEnd, 0f)
    }

    @Test
    fun incomingCornersMirrorOutgoing() {
        val single = bubbleCorners(BubblePosition.SINGLE, isMe = false)
        assertEquals(18f, single.topStart, 0f)
        assertEquals(18f, single.topEnd, 0f)
        assertEquals(4f, single.bottomStart, 0f)
        assertEquals(18f, single.bottomEnd, 0f)

        val last = bubbleCorners(BubblePosition.LAST, isMe = false)
        assertEquals(4f, last.topStart, 0f)
        assertEquals(18f, last.topEnd, 0f)
        assertEquals(18f, last.bottomStart, 0f)
        assertEquals(18f, last.bottomEnd, 0f)
    }

    @Test
    fun middleBubblesAreSoftenedOnTheJoinedSideForBothSenders() {
        assertEquals(
            BubbleCorners(18f, 8f, 18f, 8f),
            bubbleCorners(BubblePosition.MIDDLE, isMe = true)
        )
        assertEquals(
            BubbleCorners(8f, 18f, 8f, 18f),
            bubbleCorners(BubblePosition.MIDDLE, isMe = false)
        )
    }

    @Test
    fun theTailCornersOfSinglyAndRunEndsAreUnchanged() {
        // The join was softened for middle bubbles only; the pointed tail at the
        // end of a run is a deliberate shape and must not drift with it.
        assertEquals(4f, bubbleCorners(BubblePosition.SINGLE, isMe = true).bottomEnd, 0f)
        assertEquals(4f, bubbleCorners(BubblePosition.FIRST, isMe = true).bottomEnd, 0f)
        assertEquals(4f, bubbleCorners(BubblePosition.LAST, isMe = true).topEnd, 0f)
        assertEquals(4f, bubbleCorners(BubblePosition.SINGLE, isMe = false).bottomStart, 0f)
        assertEquals(4f, bubbleCorners(BubblePosition.FIRST, isMe = false).bottomStart, 0f)
        assertEquals(4f, bubbleCorners(BubblePosition.LAST, isMe = false).topStart, 0f)
    }
}
