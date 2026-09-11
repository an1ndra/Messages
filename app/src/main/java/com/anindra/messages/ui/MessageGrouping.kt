package com.anindra.messages.ui

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
