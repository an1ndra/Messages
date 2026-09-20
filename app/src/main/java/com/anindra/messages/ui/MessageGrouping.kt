package com.anindra.messages.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.anindra.messages.data.Message
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
 * Corner radii for a bubble. The flat "tail" corner sits at the bottom on the
 * sender's side for a lone/first bubble and at the top for the last bubble of a
 * run; middle bubbles are fully rounded.
 */
fun bubbleCorners(position: BubblePosition, isMe: Boolean): BubbleCorners {
    val r = BUBBLE_CORNER_DP
    val tail = BUBBLE_TAIL_DP
    return when (position) {
        BubblePosition.MIDDLE -> BubbleCorners(r, r, r, r)
        BubblePosition.SINGLE, BubblePosition.FIRST ->
            if (isMe) BubbleCorners(r, r, r, tail) else BubbleCorners(r, r, tail, r)
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

private val groupTimeFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val groupDayFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private val groupDayYearFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault())

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
fun formatGroupLabel(ts: Long, now: Long = System.currentTimeMillis()): String {
    val t = at(ts)
    val dateAndTime = { date: String -> "$date \u2022 ${groupTimeFmt.format(t)}" }
    return when {
        sameDay(ts, now) -> groupTimeFmt.format(t)
        isYesterday(ts, now) -> dateAndTime("Yesterday")
        t.year == at(now).year -> dateAndTime(groupDayFmt.format(t))
        else -> dateAndTime(groupDayYearFmt.format(t))
    }
}
