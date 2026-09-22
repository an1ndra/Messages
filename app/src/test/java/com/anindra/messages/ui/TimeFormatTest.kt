package com.anindra.messages.ui

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
    fun dateTimePrefixKeepsTheChosenClock() {
        assertEquals(
            "Jan 1, 2026, 00:30",
            dateTimeFormatter("MMM d, yyyy,", true, locale).format(at(0, 30))
        )
        assertEquals(
            "Jan 1, 2026, 12:30 AM",
            dateTimeFormatter("MMM d, yyyy,", false, locale).format(at(0, 30))
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
        assertTrue(formatGroupLabel(ts, true, now = ts).matches(Regex("\\d{2}:\\d{2}")))
        assertNotEquals(
            formatGroupLabel(ts, true, now = ts),
            formatGroupLabel(ts, false, now = ts)
        )
    }
}
