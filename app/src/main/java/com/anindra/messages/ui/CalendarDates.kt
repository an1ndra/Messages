package com.anindra.messages.ui

import com.anindra.messages.data.CalendarSystem
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * Date text in the calendar the user picked (#363).
 *
 * The choice itself is made once, in [CalendarSystem.resolve]; this file only
 * renders that answer. Gregorian keeps the java.time formatter every screen
 * already used, because it is the one CLDR path testable off-device. Any other
 * calendar goes through android.icu: java.time has no Persian chronology, and
 * android.icu exposes no PersianCalendar *class* either, so the calendar is
 * requested through the `@calendar=persian` keyword of a ULocale and ICU builds
 * it internally. Verified on AOSP 11 and 12 — `getType()` reports "persian"
 * and the CLDR month names and digits come out localized (۱۴۰۵ for fa,
 * ١٤٤٨ for ar).
 */
internal fun interface DateRenderer {
    fun render(style: DateStyle, calendar: CalendarSystem, ts: Long, locale: Locale, zone: ZoneId): String
}

/** Text for the date part of [ts], in [calendar]. Time of day is calendar-agnostic. */
internal fun formatDate(
    ts: Long,
    style: DateStyle,
    calendar: CalendarSystem,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
    renderer: DateRenderer = platformDateRenderer
): String {
    val effective =
        if (calendar == CalendarSystem.AUTO) CalendarSystem.forLanguage(locale) else calendar
    return renderer.render(style, effective, ts, locale, zone)
}

/**
 * The day picked in the schedule dialog, written in the reader's calendar.
 *
 * Null when nothing is picked or the calendar is Gregorian: the platform's grid
 * already draws that one, and a second copy of the same date would just be
 * noise. The selection is UTC midnight of the picked day, so it is formatted in
 * UTC — the device zone would shift it to the previous day west of Greenwich.
 */
internal fun pickedDateInCalendar(
    selectedUtcMillis: Long?,
    calendar: CalendarSystem,
    locale: Locale = Locale.getDefault(),
    renderer: DateRenderer = platformDateRenderer
): String? {
    if (selectedUtcMillis == null || calendar == CalendarSystem.GREGORIAN) return null
    return formatDate(
        selectedUtcMillis,
        DateStyle.DayWithYear,
        calendar,
        locale,
        ZoneOffset.UTC,
        renderer
    )
}

/** The year [ts] falls in under [calendar], for the "same year?" label choice. */
internal fun displayYear(
    ts: Long,
    calendar: CalendarSystem,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault()
): Int {
    val effective =
        if (calendar == CalendarSystem.AUTO) CalendarSystem.forLanguage(locale) else calendar
    val keyword = effective.icuKeyword
        ?: return Instant.ofEpochMilli(ts).atZone(zone).year
    val icuCalendar = android.icu.util.Calendar.getInstance(
        android.icu.util.TimeZone.getTimeZone(zone.id),
        icuLocale(locale, keyword)
    )
    icuCalendar.timeInMillis = ts
    return icuCalendar.get(android.icu.util.Calendar.YEAR)
}

private val platformDateRenderer = DateRenderer { style, calendar, ts, locale, zone ->
    val keyword = calendar.icuKeyword
    if (keyword == null) {
        dateFormatter(style, locale).format(Instant.ofEpochMilli(ts).atZone(zone))
    } else {
        val formatter = icuFormatter(style, calendar, locale, zone)
        synchronized(formatter) { formatter.format(java.util.Date(ts)) }
    }
}

private val icuCache = HashMap<String, android.icu.text.DateFormat>()

internal fun clearCalendarFormatterCache() = synchronized(icuCache) { icuCache.clear() }

private fun icuLocale(locale: Locale, keyword: String): android.icu.util.ULocale =
    android.icu.util.ULocale.forLocale(locale).setKeywordValue("calendar", keyword)

private fun icuFormatter(
    style: DateStyle,
    calendar: CalendarSystem,
    locale: Locale,
    zone: ZoneId
): android.icu.text.DateFormat {
    val keyword = calendar.icuKeyword ?: error("${calendar.name} has no ICU calendar")
    val key = "${locale.toLanguageTag()}|${calendar.storageValue}|${style.skeleton}|${zone.id}"
    synchronized(icuCache) { icuCache[key] }?.let { return it }
    val uLocale = icuLocale(locale, keyword)
    val icuZone = android.icu.util.TimeZone.getTimeZone(zone.id)
    val formatter = android.icu.text.DateFormat.getPatternInstance(
        android.icu.util.Calendar.getInstance(icuZone, uLocale),
        style.skeleton,
        uLocale
    )
    // ICU's date formatter is not thread-safe like DateTimeFormatter, hence the
    // lock at the call site. Patterns are immutable once built, so the cache is
    // keyed on everything that changes one.
    if (!calendar.keepsEra && formatter is android.icu.text.SimpleDateFormat) {
        formatter.applyPattern(stripEra(formatter.toPattern()))
    }
    formatter.timeZone = icuZone
    synchronized(icuCache) { icuCache[key] = formatter }
    return formatter
}

/**
 * Drops the era CLDR puts on every non-Gregorian date: "Aban 4, 1405 AP",
 * "Jum. I 15, 1448 AH", "15 Heshvan 5787". The user picked that calendar, so
 * the era carries no information the label does not already have, and it is a
 * suffix on every single timestamp.
 *
 * [CalendarSystem.keepsEra] is the exception: a Japanese year is *only* an era
 * year, so dropping its era would leave a bare number.
 */
internal fun stripEra(pattern: String): String {
    val out = StringBuilder(pattern.length)
    var quoted = false
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        when {
            c == '\'' && i + 1 < pattern.length && pattern[i + 1] == '\'' -> {
                out.append("''")
                i++
            }
            c == '\'' -> {
                quoted = !quoted
                out.append(c)
            }
            !quoted && c == 'G' -> Unit
            !quoted && c == ' ' && out.lastOrNull() == ' ' -> Unit
            else -> out.append(c)
        }
        i++
    }
    return out.toString().replace(" ,", ",").trim()
}
