package com.anindra.messages.ui

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One month of the calendar the user reads, as the schedule grid draws it.
 *
 * The platform `DatePicker` draws Gregorian months and cannot be told otherwise,
 * which is why the schedule sheet used to show Jalali only as a hint line under
 * a Gregorian grid (#363). This is the model the grid is built from instead.
 *
 * The type is deliberately free of `android.icu`: the JVM test runner has no ICU
 * data and, worse, its android.jar stubs return 0 rather than throwing
 * (`unitTests.isReturnDefaultValues`), so a test that reached ICU here would
 * pass over silence. [IcuCalendarMonths] is the only implementation that touches
 * it, and `CalendarGridTest` holds that line.
 *
 * [firstWeekday] and [firstDayOfWeek] use ICU's numbering — 1 is Sunday through
 * 7 Saturday — because that is what the provider reads off the calendar and
 * what CLDR week data speaks.
 */
internal data class CalendarMonth(
    val year: Int,
    val month: Int,
    val monthCount: Int,
    val dayCount: Int,
    val firstWeekday: Int,
    val firstDayOfWeek: Int,
    val label: String,
    val weekdayLabels: List<String>,
    val dayLabels: List<String>
) {
    /** Days of the previous month's tail that pad the first row. */
    val leadingBlanks: Int get() = (firstWeekday - firstDayOfWeek + 7) % 7

    /** Rows of seven cells; null is a padding cell, not a day of this month. */
    val weeks: List<List<Int?>>
        get() {
            val cells = ArrayList<Int?>(leadingBlanks + dayCount + 6)
            repeat(leadingBlanks) { cells += null }
            for (day in 1..dayCount) cells += day
            while (cells.size % 7 != 0) cells += null
            return cells.chunked(7)
        }
}

/** A day of [CalendarMonth]'s calendar. */
internal data class CalendarDay(
    val year: Int,
    val month: Int,
    val day: Int,
    val monthCount: Int
)

/**
 * Month lookup for one calendar and locale, the seam [CalendarGridTest] fakes.
 *
 * Everything the grid needs from a calendar arrives through here, so the layout
 * arithmetic can be tested without ICU and the ICU binding can be swapped
 * without touching the grid.
 */
internal interface CalendarMonthSource {
    fun month(year: Int, month: Int): CalendarMonth
}

/**
 * The next or previous month, rolling the year over.
 *
 * Only ±1 is meaningful for the grid's chevrons. Stepping back out of month 0
 * has to ask which year it is landing in first: a Hebrew year has twelve or
 * thirteen months and Coptic always has thirteen, so the last month of the
 * previous year cannot be computed as a constant.
 */
internal fun CalendarMonthSource.shiftMonth(from: CalendarMonth, delta: Int): CalendarMonth {
    if (delta == 0) return from
    val next = from.month + delta
    return when {
        delta > 0 && next >= from.monthCount -> month(from.year + 1, 0)
        delta < 0 && next < 0 -> {
            val previous = month(from.year - 1, 0)
            month(previous.year, previous.monthCount - 1)
        }
        else -> month(from.year, next)
    }
}

/**
 * Midnight UTC of the device's today, which is the stamp the pickers hand back
 * for "the day I picked" (see [com.anindra.messages.data.ScheduledTime]).
 */
internal fun utcMidnightOfToday(
    zone: ZoneId = ZoneId.systemDefault(),
    now: Long = System.currentTimeMillis()
): Long = Instant.ofEpochMilli(now)
    .atZone(zone)
    .toLocalDate()
    .atStartOfDay(ZoneOffset.UTC)
    .toInstant()
    .toEpochMilli()
