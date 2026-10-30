package com.anindra.messages.ui

import com.anindra.messages.data.CalendarSystem
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatTest {

    private val locale = Locale.US

    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 1, 1, hour, minute, 0, 0, ZoneId.of("UTC"))

    @Test
    fun patternFollowsThePreference() {
        assertEquals("HH:mm", timePattern(true))
        assertEquals("h:mm a", timePattern(false))
    }

    @Test
    fun twentyFourHourFormatPadsMidnightToZero() {
        assertEquals("00:30", timeOnlyFormatter(true, locale).format(at(0, 30)))
        assertEquals("13:05", timeOnlyFormatter(true, locale).format(at(13, 5)))
    }

    @Test
    fun twelveHourFormatUsesAmPm() {
        assertEquals("12:30 AM", timeOnlyFormatter(false, locale).format(at(0, 30)))
        assertEquals("1:05 PM", timeOnlyFormatter(false, locale).format(at(13, 5)))
    }

    @Test
    fun dateTimeKeepsTheChosenClock() {
        val ts = 1_767_268_800_000L // 2026-01-01T12:00Z
        clearDateFormatterCache()
        // The platform resolver is not available off-device, so stand one in for
        // the locale this test formats in.
        dateFormatter(DateStyle.DayWithYear, Locale.US, SkeletonResolver { _, _ -> "MMM d, y" })
        // The clock is the device's, so only its shape is asserted here.
        assertTrue(
            formatDateTime(ts, DateStyle.DayWithYear, true, CalendarSystem.GREGORIAN, Locale.US)
                .matches(Regex("""Jan 1, 2026, \d{2}:\d{2}"""))
        )
        assertTrue(
            formatDateTime(ts, DateStyle.DayWithYear, false, CalendarSystem.GREGORIAN, Locale.US)
                .matches(Regex("""Jan 1, 2026, \d{1,2}:\d{2} [AP]M"""))
        )
    }

    @Test
    fun messageBubbleDropsAmPmIn24HourMode() {
        val ts = 1_700_000_000_000L
        assertTrue(formatTimeOnly(ts, true).matches(Regex("\\d{2}:\\d{2}")))
        assertNotEquals(formatTimeOnly(ts, true), formatTimeOnly(ts, false))
    }

    @Test
    fun groupLabelDropsAmPmIn24HourMode() {
        val ts = 1_700_000_000_000L
        assertTrue(
            formatGroupLabel(ts, true, "Yesterday", CalendarSystem.GREGORIAN, now = ts)
                .matches(Regex("\\d{2}:\\d{2}"))
        )
        assertNotEquals(
            formatGroupLabel(ts, true, "Yesterday", CalendarSystem.GREGORIAN, now = ts),
            formatGroupLabel(ts, false, "Yesterday", CalendarSystem.GREGORIAN, now = ts)
        )
    }

    /** The label used to be a hardcoded English "Yesterday" regardless of locale. */
    @Test
    fun groupLabelUsesTheSuppliedLocalizedLabel() {
        seedDateFormatters()
        val ts = 1_700_000_000_000L
        val yesterday = ts - 86_400_000L
        assertTrue(
            formatGroupLabel(yesterday, true, "Yesterday", CalendarSystem.GREGORIAN, now = ts)
                .startsWith("Yesterday \u2022")
        )
        assertTrue(
            formatGroupLabel(yesterday, true, "Hier", CalendarSystem.GREGORIAN, now = ts)
                .startsWith("Hier \u2022")
        )
    }

    /** formatGroupLabel resolves date patterns through the platform, which is not
     *  available off-device; pre-seeding the cache keeps the Android path out. */
    private fun seedDateFormatters() {
        clearDateFormatterCache()
        val locale = Locale.getDefault()
        DateStyle.entries.forEach { dateFormatter(it, locale, SkeletonResolver { _, s -> s }) }
    }
}
