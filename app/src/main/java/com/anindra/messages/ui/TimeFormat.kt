package com.anindra.messages.ui

import android.content.Context
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

fun dateTimeFormatter(
    datePrefix: String,
    is24Hour: Boolean,
    locale: Locale = Locale.getDefault()
): DateTimeFormatter = DateTimeFormatter.ofPattern("$datePrefix ${timePattern(is24Hour)}", locale)

fun formatDateTime(
    ts: Long,
    datePrefix: String,
    is24Hour: Boolean,
    locale: Locale = Locale.getDefault()
): String = dateTimeFormatter(datePrefix, is24Hour, locale)
    .format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))
