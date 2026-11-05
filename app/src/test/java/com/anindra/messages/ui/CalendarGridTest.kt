package com.anindra.messages.ui

import java.io.File
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schedule grid's month arithmetic.
 *
 * ICU cannot run off-device, and the JVM test runner is worse than absent: with
 * `unitTests.isReturnDefaultValues` an android.icu call returns 0 instead of
 * throwing, so a grid that reached it would draw one 31-day month that always
 * starts on Sunday and never fail. The model is therefore pure and
 * [CalendarMonthSource] is the seam; the last test here holds that line.
 */
class CalendarGridTest {

    /** Sunday-first numbering throughout, as ICU and CLDR count weekdays. */
    private class FakeMonths(private val monthsPerYear: Map<Int, Int>) : CalendarMonthSource {
        val asked = mutableListOf<Pair<Int, Int>>()

        override fun month(year: Int, month: Int): CalendarMonth {
            asked += year to month
            return CalendarMonth(
                year = year,
                month = month,
                monthCount = monthsPerYear[year] ?: 12,
                dayCount = 30,
                firstWeekday = 1,
                firstDayOfWeek = 1,
                label = "$year/$month",
                weekdayLabels = List(7) { "w$it" },
                dayLabels = (1..30).map { it.toString() }
            )
        }
    }

    private fun month(
        dayCount: Int = 31,
        firstWeekday: Int = 1,
        firstDayOfWeek: Int = 1
    ) = CalendarMonth(
        year = 1405,
        month = 7,
        monthCount = 12,
        dayCount = dayCount,
        firstWeekday = firstWeekday,
        firstDayOfWeek = firstDayOfWeek,
        label = "Aban 1405",
        weekdayLabels = List(7) { "w$it" },
        dayLabels = (1..dayCount).map { it.toString() }
    )

    @Test
    fun theFirstRowIsPaddedUpToTheWeekStart() {
        // Saturday is the 7th weekday; a Sunday-first week leaves six blanks.
        assertEquals(6, month(firstWeekday = 7, firstDayOfWeek = 1).leadingBlanks)
        assertEquals(0, month(firstWeekday = 1, firstDayOfWeek = 1).leadingBlanks)
        // Saturday-first week: a Sunday start is the last cell of the row.
        assertEquals(1, month(firstWeekday = 1, firstDayOfWeek = 7).leadingBlanks)
        assertEquals(0, month(firstWeekday = 7, firstDayOfWeek = 7).leadingBlanks)
        for (start in 1..7) {
            for (first in 1..7) {
                val blanks = month(firstWeekday = start, firstDayOfWeek = first).leadingBlanks
                assertTrue("$start/$first", blanks in 0..6)
            }
        }
    }

    @Test
    fun everyDayAppearsOnceAndOnlyFullWeeksAreDrawn() {
        for (firstWeekday in 1..7) {
            for (dayCount in listOf(29, 30, 31)) {
                val weeks = month(dayCount = dayCount, firstWeekday = firstWeekday).weeks
                assertTrue("$firstWeekday/$dayCount", weeks.all { it.size == 7 })
                val days = weeks.flatten().filterNotNull()
                assertEquals("$firstWeekday/$dayCount", (1..dayCount).toList(), days)
            }
        }
    }

    @Test
    fun aFourWeekMonthNeedsFourRows() {
        val weeks = month(dayCount = 28, firstWeekday = 1, firstDayOfWeek = 1).weeks
        assertEquals(4, weeks.size)
        assertEquals(listOf(1), weeks.first().take(1))
    }

    @Test
    fun theWeeksArePaddedSoTheLastRowIsComplete() {
        // 30 days starting on a Saturday: six blanks + 30 = 36, padded to 42.
        val weeks = month(dayCount = 30, firstWeekday = 7).weeks
        assertEquals(6, weeks.size)
        assertEquals(6, weeks.last().count { it == null })
    }

    @Test
    fun theNextMonthRollsIntoTheNextYear() {
        val source = FakeMonths(emptyMap())
        val december = source.month(1405, 11)
        assertEquals(1406 to 0, source.shiftMonth(december, 1).let { it.year to it.month })
    }

    @Test
    fun thePreviousMonthRollsBackIntoTheLastMonthOfThePreviousYear() {
        val source = FakeMonths(mapOf(1404 to 12))
        val first = source.month(1405, 0)
        val previous = source.shiftMonth(first, -1)
        assertEquals(1404 to 11, previous.year to previous.month)
    }

    /**
     * A Hebrew leap year has thirteen months, so the month before the year's
     * first one is not always number eleven.
     */
    @Test
    fun steppingBackUsesThePreviousYearsOwnMonthCount() {
        val source = FakeMonths(mapOf(5784 to 13))
        val previous = source.shiftMonth(source.month(5785, 0), -1)
        assertEquals(5784 to 12, previous.year to previous.month)
    }

    @Test
    fun steppingWithinAYearKeepsTheYear() {
        val source = FakeMonths(emptyMap())
        val shifted = source.shiftMonth(source.month(1405, 3), 1)
        assertEquals(1405 to 4, shifted.year to shifted.month)
        assertSame(shifted, source.shiftMonth(shifted, 0))
    }

    @Test
    fun todayIsStampedAsMidnightUtcOfTheLocalDay() {
        // 17:00Z is 22:30 in Kolkata: still the same local day, so the stamp
        // must be that day's UTC midnight and not shift either way.
        val evening = 1_793_466_000_000L // 2026-10-31T17:00Z
        val kolkata = utcMidnightOfToday(ZoneId.of("Asia/Kolkata"), evening)
        assertEquals(1_793_404_800_000L, kolkata) // 2026-10-31T00:00Z
    }

    /** East of Greenwich late in the UTC day, the local day is already tomorrow. */
    @Test
    fun theStampFollowsTheDeviceDayNotUtc() {
        val evening = 1_793_466_000_000L // 2026-10-31T16:00Z → 2026-11-01 in Tokyo
        val tokyo = utcMidnightOfToday(ZoneId.of("Asia/Tokyo"), 1_793_462_400_000L)
        assertEquals(1_793_491_200_000L, tokyo) // 2026-11-01T00:00Z
        assertEquals(1_793_404_800_000L, utcMidnightOfToday(ZoneId.of("America/New_York"), evening))
    }

    /**
     * The guard for the trap in this file's header: an ICU call inside the model
     * would silently return zeros in JVM tests rather than failing.
     */
    @Test
    fun theGridModelDoesNotReachForIcu() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/anindra/messages/ui") }
            .firstOrNull { it.isDirectory }
            ?: error("ui sources not found")
        val model = File(root, "CalendarGrid.kt").readText()
        assertFalse(
            "CalendarGrid.kt must stay free of android.icu: the JVM runner would " +
                "return zeros and the tests would pass over silence",
            Regex("import\\s+android\\.icu|android\\.icu\\.(util|text|impl)").containsMatchIn(model)
        )
    }
}
