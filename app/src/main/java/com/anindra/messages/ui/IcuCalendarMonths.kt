package com.anindra.messages.ui

import android.icu.text.DecimalFormatSymbols
import android.icu.util.Calendar
import android.icu.util.ULocale
import com.anindra.messages.data.CalendarSystem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * The schedule grid's month data, read out of android.icu.
 *
 * ICU is the only source that can draw any of the calendars the settings offer:
 * `java.time` has no Persian chronology and no Hebrew or Coptic one either, and
 * the calendar is reached through the `@calendar=` keyword of a [ULocale] the
 * same way [CalendarDates] reaches it for text.
 *
 * Two conversions bracket the whole feature. A cell is turned into the stamp the
 * scheduler already stores — midnight **UTC of the Gregorian day** the cell
 * falls on, exactly what `ScheduledTime.selectedDate` expects — and that stamp
 * is turned back into a cell by reading its UTC date first. The date is anchored
 * at noon in the device zone on both sides: a calendar day starts at local
 * midnight, so converting at UTC midnight would land a Persian cell on the
 * previous day for anyone west of Greenwich.
 */
internal class IcuCalendarMonths(
    private val calendar: CalendarSystem,
    private val locale: Locale = Locale.getDefault(),
    private val zone: ZoneId = ZoneId.systemDefault()
) : CalendarMonthSource {

    private val uLocale: ULocale = ULocale.forLocale(locale)
        .setKeywordValue("calendar", calendar.icuKeyword ?: "gregorian")

    private val zeroDigit: Char =
        DecimalFormatSymbols.getInstance(uLocale).zeroDigit

    private fun instance(): Calendar =
        Calendar.getInstance(android.icu.util.TimeZone.getTimeZone(zone.id), uLocale)

    override fun month(year: Int, month: Int): CalendarMonth {
        val icu = instance()
        icu.clear()
        // Noon keeps the cell on the same day across a DST transition.
        icu.set(year, month, 1, 12, 0, 0)
        val firstDayOfWeek = icu.firstDayOfWeek
        val dayCount = icu.getActualMaximum(Calendar.DAY_OF_MONTH)
        return CalendarMonth(
            year = year,
            month = month,
            monthCount = icu.getActualMaximum(Calendar.MONTH) + 1,
            dayCount = dayCount,
            firstWeekday = icu.get(Calendar.DAY_OF_WEEK),
            firstDayOfWeek = firstDayOfWeek,
            label = formatDate(
                icu.timeInMillis,
                DateStyle.MonthAndYear,
                calendar = this.calendar,
                locale = locale,
                zone = zone
            ),
            weekdayLabels = weekdayLabels(firstDayOfWeek),
            dayLabels = (1..dayCount).map { dayLabel(it) }
        )
    }

    /** Which day of which month the picker's UTC stamp names. */
    fun dayOf(utcMillis: Long): CalendarDay {
        val gregorianDay = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return dayOf(gregorianDay)
    }

    /** Today in this calendar, for the grid's "you are here" marker. */
    fun today(): CalendarDay = dayOf(LocalDate.now(zone))

    /**
     * The stamp the picker hands back for [year]/[month]/[day] of this calendar.
     *
     * The fields set here belong to *this* calendar — 4 Aban 1405 sets year 1405
     * — so the instant is read back out in the device zone to find which
     * Gregorian day it landed on.
     */
    fun utcMillisOf(year: Int, month: Int, day: Int): Long {
        val icu = instance()
        icu.clear()
        icu.set(year, month, day, 12, 0, 0)
        return Instant.ofEpochMilli(icu.timeInMillis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * The calendar day a Gregorian date falls on.
     *
     * The fields are read from the calendar, so they cannot also be written to
     * it: setting (2026, 9, 31) on a Persian calendar would mean the year 2026
     * of the Persian calendar, not October 2026. The instant is anchored at noon
     * in the device zone and only then handed to the calendar.
     */
    private fun dayOf(date: LocalDate): CalendarDay {
        val icu = instance()
        icu.timeInMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return CalendarDay(
            year = icu.get(Calendar.YEAR),
            month = icu.get(Calendar.MONTH),
            day = icu.get(Calendar.DAY_OF_MONTH),
            monthCount = icu.getActualMaximum(Calendar.MONTH) + 1
        )
    }

    /** The month [utcMillis] falls in, or today's month when nothing is picked. */
    fun monthOf(utcMillis: Long?): CalendarMonth {
        val day = utcMillis?.let { dayOf(it) } ?: today()
        return month(day.year, day.month)
    }

    /** [value] in this locale's digits — ۱۴۰۵ rather than 1405 under `fa`. */
    fun numberLabel(value: Int): String =
        value.toString().map { zeroDigit + (it - '0') }.joinToString("")

    private fun weekdayLabels(firstDayOfWeek: Int): List<String> {
        // Narrow names, as Material's own picker uses: "S M T W T F S" in
        // English, not "Sun Mon Tue". android.icu's DateFormatSymbols exposes
        // short and wide names only, so the narrow ones are formatted from a
        // known Sunday (2026-03-01) and rotated to the calendar's week start.
        val format = android.icu.text.SimpleDateFormat("EEEEE", uLocale)
        format.timeZone = android.icu.util.TimeZone.getTimeZone("UTC")
        val names = (0L until 7L).map { offset ->
            val day = LocalDate.of(2026, 3, 1).plusDays(offset)
            val instant = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            format.format(java.util.Date(instant))
        }
        return names.drop(firstDayOfWeek - 1) + names.take(firstDayOfWeek - 1)
    }

    private fun dayLabel(day: Int): String = numberLabel(day)
}
