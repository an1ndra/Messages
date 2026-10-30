package com.anindra.messages.ui

import android.content.Context
import com.anindra.messages.data.CalendarSystem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun is24HourFormat(context: Context): Boolean =
    android.text.format.DateFormat.is24HourFormat(context)

fun timePattern(is24Hour: Boolean): String = if (is24Hour) "HH:mm" else "h:mm a"

private val timeFormat12: DateTimeFormatter =
    DateTimeFormatter.ofPattern(timePattern(false), Locale.getDefault())
private val timeFormat24: DateTimeFormatter =
    DateTimeFormatter.ofPattern(timePattern(true), Locale.getDefault())

fun timeOnlyFormatter(is24Hour: Boolean): DateTimeFormatter =
    if (is24Hour) timeFormat24 else timeFormat12

fun timeOnlyFormatter(is24Hour: Boolean, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(timePattern(is24Hour), locale)

/**
 * A timestamp as "date, time", with the date in the chosen [calendar]. The
 * clock is unaffected: hours and minutes read the same in every calendar.
 */
fun formatDateTime(
    ts: Long,
    style: DateStyle,
    is24Hour: Boolean,
    calendar: CalendarSystem,
    locale: Locale = Locale.getDefault()
): String {
    val zone = ZoneId.systemDefault()
    val date = formatDate(ts, style, calendar, locale, zone)
    val time = timeOnlyFormatter(is24Hour, locale).format(Instant.ofEpochMilli(ts).atZone(zone))
    return "$date, $time"
}
