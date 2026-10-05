package com.anindra.messages.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The app used to format dates with literal patterns such as "MMM d". Field order
 * is locale data, not formatting trivia: "MMM d" yields "Mar 3" in English but
 * "3 mars" in French, and the hardcoded pattern produced the English order in
 * every language. These tests pin the fix using a stand-in resolver, because the
 * real one lives in android.text.format.DateFormat and cannot run off-device.
 *
 * The CLDR patterns the resolver returns on-device are checked end-to-end by
 * scripts/test-date-locale.sh.
 */
class DatePatternsTest {

    /** Real CLDR patterns for the skeletons this app uses. */
    private val cldr = mapOf(
        "MMMd" to mapOf("en" to "MMM d", "fr" to "d MMM", "de" to "d. MMM", "ja" to "M月d日"),
        "MMMEd" to mapOf("en" to "EEE, MMM d", "fr" to "EEE d MMM", "ja" to "M月d日(E)"),
        "MMMMEEEEd" to mapOf("en" to "EEEE, MMMM d", "fr" to "EEEE d MMMM"),
        "yMMMMEEEEd" to mapOf("en" to "EEEE, MMMM d, y", "fr" to "EEEE d MMMM y"),
    )

    private val resolver = SkeletonResolver { locale, skeleton ->
        cldr[skeleton]?.get(locale.language)
            ?: error("no test pattern for $skeleton/${locale.language}")
    }

    private val sample: LocalDate = LocalDate.of(2024, 8, 2)

    @Before
    fun setUp() = clearDateFormatterCache()

    @Test
    fun everyStyleUsesAKnownSkeleton() {
        val known = cldr.keys
        for (style in DateStyle.entries) {
            assertTrue("${style.name} skeleton ${style.skeleton} is not covered by the test data", style.skeleton in known)
        }
    }

    @Test
    fun skeletonsNeverPinTheFieldOrder() {
        // A skeleton lists fields; it must not contain the literal separators and
        // ordering that make a pattern locale-specific in the first place.
        for (style in DateStyle.entries) {
            assertTrue(
                "${style.name} looks like a literal pattern, not a skeleton",
                style.skeleton.none { it == '/' || it == '.' || it == ',' }
            )
        }
    }

    @Test
    fun dayOrderFollowsTheLocale() {
        val en = dateFormatter(DateStyle.Day, Locale.ENGLISH, resolver).format(sample)
        val fr = dateFormatter(DateStyle.Day, Locale.FRENCH, resolver).format(sample)
        assertEquals("Aug 2", en)
        assertEquals("2 août", fr)
        assertNotEquals("French must not reuse the English field order", en, fr)
    }

    @Test
    fun japaneseUsesItsOwnOrderAndCharacters() {
        val ja = dateFormatter(DateStyle.Day, Locale.JAPANESE, resolver).format(sample)
        assertEquals("8月2日", ja)
    }

    @Test
    fun groupLabelsFollowTheLocale() {
        val en = dateFormatter(DateStyle.DayWithFullWeekday, Locale.ENGLISH, resolver).format(sample)
        val fr = dateFormatter(DateStyle.DayWithFullWeekday, Locale.FRENCH, resolver).format(sample)
        assertEquals("Friday, August 2", en)
        assertEquals("vendredi 2 août", fr)
    }

    @Test
    fun yearStyleKeepsTheYearLast() {
        val fr = dateFormatter(DateStyle.DayWithFullWeekdayAndYear, Locale.FRENCH, resolver).format(sample)
        assertEquals("vendredi 2 août 2024", fr)
    }

    /**
     * The original defect in the caching: formatters were top-level vals built
     * from Locale.getDefault() during class initialisation, so a per-app
     * language change left the process formatting in the old language forever.
     */
    @Test
    fun switchingLocaleDoesNotReturnTheStaleFormatter() {
        val asFrench = dateFormatter(DateStyle.Day, Locale.FRENCH, resolver)
        assertEquals("2 août", asFrench.format(sample))

        val asEnglish = dateFormatter(DateStyle.Day, Locale.ENGLISH, resolver)
        assertEquals("Aug 2", asEnglish.format(sample))

        assertEquals("returning to French must still be French", "2 août", asFrench.format(sample))
    }

    @Test
    fun cacheIsKeyedByLocaleAndSkeleton() {
        val a = dateFormatter(DateStyle.Day, Locale.FRENCH, resolver)
        val b = dateFormatter(DateStyle.Day, Locale.FRENCH, resolver)
        val c = dateFormatter(DateStyle.DayWithFullWeekday, Locale.FRENCH, resolver)
        val d = dateFormatter(DateStyle.Day, Locale.GERMAN, resolver)
        assertTrue("same locale and skeleton should reuse the formatter", a === b)
        assertNotEquals("different skeleton must not share a formatter", a === c, true)
        assertNotEquals("different locale must not share a formatter", a === d, true)
    }

    @Test
    fun cachedFormatterIsStillUsableAfterClear() {
        val before = dateFormatter(DateStyle.Day, Locale.FRENCH, resolver)
        clearDateFormatterCache()
        val after = dateFormatter(DateStyle.Day, Locale.FRENCH, resolver)
        assertEquals(before.format(sample), after.format(sample))
        assertTrue("clear should drop the cached instance", before !== after)
    }

    @Test
    fun formatterIsBuiltFromTheResolvedPatternAndLocale() {
        val f: DateTimeFormatter = dateFormatter(DateStyle.Day, Locale.FRENCH, resolver)
        assertEquals("2 août", f.format(sample))
        assertEquals("1 févr.", f.format(LocalDate.of(2024, 2, 1)))
    }
}