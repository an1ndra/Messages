package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ScheduledTimeTest {

    private val chicago = ZoneId.of("America/Chicago")
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val utc = ZoneOffset.UTC

    /** Material's DatePicker hands back midnight UTC of the tapped day. */
    private fun pickerValue(date: LocalDate): Long =
        date.atStartOfDay(utc).toInstant().toEpochMilli()

    // --- the reported bug: picking "today" produced yesterday ------------

    @Test
    fun pickerValueIsReadAsTheCalendarDayNotShiftedByTheZone() {
        assertEquals(
            LocalDate.of(2026, 9, 28),
            ScheduledTime.selectedDate(pickerValue(LocalDate.of(2026, 9, 28)))
        )
    }

    @Test
    fun localStartOfDayStaysOnTheTappedDayWestOfUtc() {
        val start = ScheduledTime.localStartOfDay(pickerValue(LocalDate.of(2026, 9, 28)), chicago)
        val asLocal = java.time.Instant.ofEpochMilli(start).atZone(chicago).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 28), asLocal)
    }

    @Test
    fun localStartOfDayStaysOnTheTappedDayEastOfUtc() {
        val start = ScheduledTime.localStartOfDay(pickerValue(LocalDate.of(2026, 9, 28)), kolkata)
        val asLocal = java.time.Instant.ofEpochMilli(start).atZone(kolkata).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 28), asLocal)
    }

    @Test
    fun startOfDayIsMidnightInTheGivenZone() {
        val start = ScheduledTime.localStartOfDay(pickerValue(LocalDate.of(2026, 9, 28)), chicago)
        val local = java.time.Instant.ofEpochMilli(start).atZone(chicago)
        assertEquals(0, local.hour)
        assertEquals(0, local.minute)
    }

    // --- time-of-day -----------------------------------------------------

    @Test
    fun atTimeAppliesThePickedHourAndMinute() {
        val ts = ScheduledTime.atTime(pickerValue(LocalDate.of(2026, 9, 28)), 16, 36, chicago)
        val local = java.time.Instant.ofEpochMilli(ts).atZone(chicago)
        assertEquals(LocalDate.of(2026, 9, 28), local.toLocalDate())
        assertEquals(16, local.hour)
        assertEquals(36, local.minute)
        assertEquals(0, local.second)
    }

    @Test
    fun atTimeIsNotTheUtcMidnightThePickerReturned() {
        val picked = pickerValue(LocalDate.of(2026, 9, 28))
        assertTrue(ScheduledTime.atTime(picked, 9, 0, chicago) != picked)
    }

    @Test
    fun outOfRangeTimePartsAreClamped() {
        val ts = ScheduledTime.atTime(pickerValue(LocalDate.of(2026, 9, 28)), 99, 99, chicago)
        val local = java.time.Instant.ofEpochMilli(ts).atZone(chicago)
        assertEquals(23, local.hour)
        assertEquals(59, local.minute)
    }

    // --- resolve: never hand the repository a past timestamp -------------

    @Test
    fun resolveKeepsAFutureTimeOnTheChosenDay() {
        val now = ScheduledTime.atTime(pickerValue(LocalDate.of(2026, 9, 28)), 9, 0, chicago)
        val picked = pickerValue(LocalDate.of(2026, 9, 28))
        val ts = ScheduledTime.resolve(picked, 16, 36, chicago, now)
        assertEquals(ts, ScheduledTime.atTime(picked, 16, 36, chicago))
    }

    @Test
    fun resolveRollsAPastTimeForwardToTomorrowSoItStaysInTheFuture() {
        // The reported crash: "today" plus a time that had already gone by.
        val now = ScheduledTime.atTime(pickerValue(LocalDate.of(2026, 9, 28)), 15, 36, chicago)
        val picked = pickerValue(LocalDate.of(2026, 9, 28))
        val ts = ScheduledTime.resolve(picked, 9, 0, chicago, now)
        assertTrue("must be in the future, was $ts vs $now", ts > now)
        val local = java.time.Instant.ofEpochMilli(ts).atZone(chicago)
        assertEquals(LocalDate.of(2026, 9, 29), local.toLocalDate())
        assertEquals(9, local.hour)
    }

    @Test
    fun resolveKeepsTheTimeOfDayEvenWhenItRollsToTomorrow() {
        val now = ScheduledTime.atTime(pickerValue(LocalDate.of(2026, 9, 28)), 15, 36, chicago)
        val ts = ScheduledTime.resolve(pickerValue(LocalDate.of(2026, 9, 28)), 9, 5, chicago, now)
        val local = java.time.Instant.ofEpochMilli(ts).atZone(chicago)
        assertEquals(9, local.hour)
        assertEquals(5, local.minute)
    }

    @Test
    fun resolveIsAlwaysStrictlyInTheFuture() {
        val picked = pickerValue(LocalDate.of(2026, 9, 28))
        val now = ScheduledTime.atTime(picked, 15, 36, chicago)
        for (hour in 0..23) {
            for (minute in 0..59) {
                val ts = ScheduledTime.resolve(picked, hour, minute, chicago, now)
                assertTrue(
                    "hour=$hour minute=$minute produced $ts (now=$now)",
                    ts > now
                )
            }
        }
    }

    @Test
    fun resolveDoesNotRollBackwardsOnAZoneEastOfUtc() {
        val now = ScheduledTime.atTime(pickerValue(LocalDate.of(2026, 9, 28)), 15, 36, kolkata)
        val picked = pickerValue(LocalDate.of(2026, 9, 28))
        val ts = ScheduledTime.resolve(picked, 18, 0, kolkata, now)
        val local = java.time.Instant.ofEpochMilli(ts).atZone(kolkata)
        assertEquals(LocalDate.of(2026, 9, 28), local.toLocalDate())
        assertEquals(18, local.hour)
    }
}
