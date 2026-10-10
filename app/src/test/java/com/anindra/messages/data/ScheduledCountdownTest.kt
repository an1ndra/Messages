package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class ScheduledCountdownTest {

    private val now = 1_000_000_000L
    private fun after(millis: Long) = now + millis

    @Test
    fun formatsHoursAndMinutes() {
        assertEquals("2h 14m", ScheduledCountdown.format(now, after(TimeUnit.HOURS.toMillis(2) + TimeUnit.MINUTES.toMillis(14))))
    }

    @Test
    fun formatsWholeDays() {
        assertEquals("1d 3h", ScheduledCountdown.format(now, after(TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(3))))
    }

    @Test
    fun underAnHourShowsMinutesAndSeconds() {
        assertEquals("14m 5s", ScheduledCountdown.format(now, after(TimeUnit.MINUTES.toMillis(14) + 5_000L)))
    }

    @Test
    fun underAMinuteCountsInSecondsRatherThanReadingZero() {
        // "0m" would read as overdue; seconds are honest about how close it is.
        assertEquals("30s", ScheduledCountdown.format(now, after(30_000L)))
        assertEquals("1s", ScheduledCountdown.format(now, after(1_000L)))
    }

    @Test
    fun aPassedTimeShowsNothing() {
        assertEquals("", ScheduledCountdown.format(now, now - 1))
        assertEquals("", ScheduledCountdown.format(now, now))
    }

    @Test
    fun remainingNeverGoesNegative() {
        assertEquals(0L, ScheduledCountdown.remainingMillis(now, now - 5_000L))
        assertEquals(5_000L, ScheduledCountdown.remainingMillis(now, now + 5_000L))
    }

    @Test
    fun ticksFastOnlyInTheFinalMinute() {
        val far = after(TimeUnit.HOURS.toMillis(5))
        assertEquals(60_000L, ScheduledCountdown.refreshIntervalMillis(now, far))

        val near = after(TimeUnit.SECONDS.toMillis(30))
        assertEquals(1_000L, ScheduledCountdown.refreshIntervalMillis(now, near))

        val past = now - 1_000L
        assertEquals(1_000L, ScheduledCountdown.refreshIntervalMillis(now, past))
    }

    @Test
    fun exactlyOneDayHasNoHoursComponent() {
        assertEquals("1d 0h", ScheduledCountdown.format(now, after(TimeUnit.DAYS.toMillis(1))))
    }

    @Test
    fun exactlyOneHourDropsTheSecondsComponent() {
        assertEquals("1h 0m", ScheduledCountdown.format(now, after(TimeUnit.HOURS.toMillis(1))))
    }
}