package com.anindra.messages.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.anindra.messages.data.Message
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Consecutive messages form a "run". A run breaks on a day change or a gap
 *  longer than [GROUP_GAP_MS].
 *
 *  A sender change deliberately does NOT break a run: Google Messages keeps
 *  alternating messages in one run when they're close together. Verified on an
 *  emulator by injecting an inbound and an outbound message 5 minutes apart —
 *  no separator appeared between them, whereas a >1 hour gap and a day change
 *  both produced one. */
const val GROUP_GAP_MS = 60L * 60L * 1000L

fun startsNewGroup(previous: Message, current: Message): Boolean =
    !sameDay(previous.timestamp, current.timestamp) ||
        current.timestamp - previous.timestamp > GROUP_GAP_MS

/** Where a bubble sits in a run of consecutive messages from the same sender. */
enum class BubblePosition { SINGLE, FIRST, MIDDLE, LAST }

/** Corner radii in dp, clockwise from the top-start corner. */
data class BubbleCorners(
    val topStart: Float,
    val topEnd: Float,
    val bottomStart: Float,
    val bottomEnd: Float
)

private const val BUBBLE_CORNER_DP = 18f
private const val BUBBLE_TAIL_DP = 4f

/**
 * Radius on the side where a middle bubble meets its neighbours. Kept separate
 * from [BUBBLE_TAIL_DP] because the two are different jobs that merely happened
 * to share a value: the tail is the pointed corner at the end of a message,
 * this is the join inside a stack. Sharing one constant made them impossible to
 * tune independently.
 */
private const val BUBBLE_JOINED_DP = 8f

/**
 * Position of [index] within its sender run. A run is a maximal stretch of
 * same-sender messages that [startsNewGroup] would not split (same day, gap
 * <= [GROUP_GAP_MS]). Alternating senders are always separate runs.
 */
fun bubblePosition(messages: List<Message>, index: Int): BubblePosition {
    val current = messages[index]
    val previous = messages.getOrNull(index - 1)
    val next = messages.getOrNull(index + 1)
    val joinsPrevious = previous != null &&
        previous.isMe == current.isMe &&
        !startsNewGroup(previous, current)
    val joinsNext = next != null &&
        next.isMe == current.isMe &&
        !startsNewGroup(current, next)
    return when {
        joinsPrevious && joinsNext -> BubblePosition.MIDDLE
        joinsPrevious -> BubblePosition.LAST
        joinsNext -> BubblePosition.FIRST
        else -> BubblePosition.SINGLE
    }
}

/**
 * Corner radii for a bubble. A lone bubble and the first of a run carry the
 * "tail": only the bottom corner on the sender's stack side — start (left)
 * for received bubbles, end (right) for sent ones — is flat, the other three
 * stay rounded. Inside a run the reduced corner sits wherever a bubble touches
 * a neighbour — both stack-side corners for a middle bubble, the top one for
 * the last — so a stack reads as one connected block. The join is softened
 * rather than square: it still reads as joined because bubbles in a run sit
 * 3dp apart, but it no longer looks cut off.
 */
fun bubbleCorners(position: BubblePosition, isMe: Boolean): BubbleCorners {
    val r = BUBBLE_CORNER_DP
    val tail = BUBBLE_TAIL_DP
    val joined = BUBBLE_JOINED_DP
    return when (position) {
        BubblePosition.SINGLE, BubblePosition.FIRST ->
            if (isMe) BubbleCorners(r, r, r, tail) else BubbleCorners(r, r, tail, r)
        BubblePosition.MIDDLE ->
            if (isMe) BubbleCorners(r, joined, r, joined) else BubbleCorners(joined, r, joined, r)
        BubblePosition.LAST ->
            if (isMe) BubbleCorners(r, tail, r, r) else BubbleCorners(tail, r, r, r)
    }
}

fun BubbleCorners.toShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = topStart.dp,
    topEnd = topEnd.dp,
    bottomStart = bottomStart.dp,
    bottomEnd = bottomEnd.dp
)

private fun at(ts: Long): ZonedDateTime =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())

private fun isYesterday(ts: Long, now: Long): Boolean =
    at(ts).toLocalDate() == at(now).toLocalDate().minusDays(1)

/**
 * Label for a group, taken from its FIRST message. Mirrors Google Messages:
 *  - today      -> "9:20 PM"
 *  - yesterday  -> "Yesterday • 9:20 PM"
 *  - same year  -> "Sunday, Aug 2 • 3:15 PM"
 *  - older      -> "Sunday, Aug 2, 2024 • 3:15 PM"
 */
fun formatGroupLabel(ts: Long, is24Hour: Boolean, yesterdayLabel: String, now: Long = System.currentTimeMillis()): String {
    val t = at(ts)
    val timeFmt = timeOnlyFormatter(is24Hour)
    val dateAndTime = { date: String -> "$date \u2022 ${timeFmt.format(t)}" }
    return when {
        sameDay(ts, now) -> timeFmt.format(t)
        isYesterday(ts, now) -> dateAndTime(yesterdayLabel)
        t.year == at(now).year -> dateAndTime(dateFormatter(DateStyle.DayWithFullWeekday).format(t))
        else -> dateAndTime(dateFormatter(DateStyle.DayWithFullWeekdayAndYear).format(t))
    }
}
