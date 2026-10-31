package com.anindra.messages.ui

import com.anindra.messages.data.CalendarSystem
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calendar choice (#363). ICU itself cannot run off-device, so these tests
 * pin the decisions — which calendar a language gets, which calendar reaches
 * the renderer, how the resolved pattern is cleaned — while the rendering
 * itself is checked on-device by scripts/test-calendar.sh.
 */
class CalendarDatesTest {

    private val fa = Locale.forLanguageTag("fa-IR")
    private val ar = Locale.forLanguageTag("ar-EG")
    private val en = Locale.ENGLISH

    @Test
    fun storedValuesRoundTrip() {
        for (system in CalendarSystem.entries) {
            assertEquals(system, CalendarSystem.fromStorage(system.storageValue))
        }
    }

    @Test
    fun unknownOrMissingStorageFallsBackToAutomatic() {
        assertEquals(CalendarSystem.AUTO, CalendarSystem.fromStorage(null))
        assertEquals(CalendarSystem.AUTO, CalendarSystem.fromStorage(""))
        assertEquals(CalendarSystem.AUTO, CalendarSystem.fromStorage("julian"))
    }

    /** These strings are preferences keys in all but name: renaming one silently
     *  resets the choice on every install that had picked it. */
    @Test
    fun storageValuesAreStable() {
        assertEquals(
            listOf(
                "auto", "gregorian", "persian", "hijri_umalqura", "hebrew",
                "buddhist", "japanese", "indian", "coptic"
            ),
            CalendarSystem.entries.map { it.storageValue }
        )
    }

    @Test
    fun persianPhonesDefaultToJalali() {
        assertEquals(CalendarSystem.PERSIAN, CalendarSystem.forLanguage(fa))
    }

    @Test
    fun arabicPhonesDefaultToHijri() {
        assertEquals(CalendarSystem.HIJRI_UMALQURA, CalendarSystem.forLanguage(ar))
    }

    @Test
    fun otherLanguagesDefaultToGregorian() {
        for (tag in listOf("en-US", "de-DE", "ja-JP", "tr-TR", "hi-IN", "pt-BR")) {
            assertEquals(tag, CalendarSystem.GREGORIAN, CalendarSystem.forLanguage(Locale.forLanguageTag(tag)))
        }
    }

    @Test
    fun anExplicitChoiceBeatsTheLanguage() {
        assertEquals(CalendarSystem.GREGORIAN, CalendarSystem.resolve("gregorian", fa))
        assertEquals(CalendarSystem.PERSIAN, CalendarSystem.resolve("persian", ar))
        assertEquals(CalendarSystem.HEBREW, CalendarSystem.resolve("hebrew", en))
    }

    @Test
    fun automaticResolvesByLanguage() {
        assertEquals(CalendarSystem.PERSIAN, CalendarSystem.resolve("auto", fa))
        assertEquals(CalendarSystem.HIJRI_UMALQURA, CalendarSystem.resolve("auto", ar))
        assertEquals(CalendarSystem.GREGORIAN, CalendarSystem.resolve(null, en))
    }

    @Test
    fun resolvingNeverLeftAutomaticAsTheCalendarToRender() {
        for (system in CalendarSystem.entries) {
            for (locale in listOf(fa, ar, en)) {
                assertTrue(
                    "${system.storageValue}/${locale.language} resolved back to AUTO",
                    CalendarSystem.resolve(system.storageValue, locale) != CalendarSystem.AUTO
                )
            }
        }
    }

    /** Only Gregorian may use the off-device-tested java.time path; every other
     *  calendar must name the ICU keyword that renders it. */
    @Test
    fun everyNonGregorianCalendarNamesItsIcuKeyword() {
        assertNull(CalendarSystem.AUTO.icuKeyword)
        assertNull(CalendarSystem.GREGORIAN.icuKeyword)
        for (system in CalendarSystem.entries - CalendarSystem.AUTO - CalendarSystem.GREGORIAN) {
            assertTrue("${system.name} has no ICU keyword", !system.icuKeyword.isNullOrBlank())
        }
        val keywords = CalendarSystem.entries.mapNotNull { it.icuKeyword }
        assertEquals("two calendars share an ICU keyword", keywords.size, keywords.toSet().size)
    }

    @Test
    fun formattingPassesTheResolvedCalendarToTheRenderer() {
        val seen = mutableListOf<CalendarSystem>()
        val recorder = DateRenderer { _, calendar, _, _, _ -> seen += calendar; "" }
        formatDate(0L, DateStyle.Day, CalendarSystem.PERSIAN, en, ZoneId.of("UTC"), recorder)
        formatDate(0L, DateStyle.Day, CalendarSystem.AUTO, fa, ZoneId.of("UTC"), recorder)
        formatDate(0L, DateStyle.Day, CalendarSystem.AUTO, en, ZoneId.of("UTC"), recorder)
        assertEquals(
            listOf(CalendarSystem.PERSIAN, CalendarSystem.PERSIAN, CalendarSystem.GREGORIAN),
            seen
        )
    }

    @Test
    fun formattingHandsTheSkeletonAndZoneThrough() {
        var style: DateStyle? = null
        var zone: ZoneId? = null
        val recorder = DateRenderer { s, _, _, _, z -> style = s; zone = z; "" }
        val tokyo = ZoneId.of("Asia/Tokyo")
        formatDate(0L, DateStyle.DayWithFullWeekdayAndYear, CalendarSystem.JAPANESE, en, tokyo, recorder)
        assertEquals(DateStyle.DayWithFullWeekdayAndYear, style)
        assertEquals(tokyo, zone)
    }

    /** CLDR appends an era to every non-Gregorian date; the app drops it. */
    @Test
    fun theEraSuffixIsStrippedFromResolvedPatterns() {
        assertEquals("MMM d, y", stripEra("MMM d, y G"))
        assertEquals("d MMMM y", stripEra("d MMMM y G"))
        assertEquals("EEEE, d MMMM y", stripEra("EEEE, d MMMM y G"))
        assertEquals("MMM d", stripEra("MMM d"))
        assertEquals("d MMM y", stripEra("d MMM y"))
    }

    @Test
    fun quotedTextIsNotMistakenForAnEra() {
        assertEquals("MMM d 'G'", stripEra("MMM d 'G'"))
        assertEquals("MMM d, y", stripEra("MMM d, y G"))
    }

    /** A Japanese year is an era year: "Oct 26, 8" alone reads as a mistake. */
    @Test
    fun onlyTheJapaneseCalendarKeepsItsEra() {
        assertEquals(
            listOf(CalendarSystem.JAPANESE),
            CalendarSystem.entries.filter { it.keepsEra }
        )
    }

    /** The grid already speaks Gregorian, so the echo only exists for the others. */
    @Test
    fun theGregorianEchoIsSkippedForGregorianAndEmptySelections() {
        assertNull(pickedDateInGregorian(null, CalendarSystem.PERSIAN))
        assertNull(pickedDateInGregorian(0L, CalendarSystem.GREGORIAN))
    }

    /**
     * The grid hands back UTC midnight of the picked day, so the echo must be
     * formatted in UTC or every zone west of Greenwich shows the previous day.
     */
    @Test
    fun theGregorianEchoIsFormattedInUtcAndInGregorian() {
        var zone: ZoneId? = null
        var style: DateStyle? = null
        var seen: CalendarSystem? = null
        var ts: Long? = null
        val recorder = DateRenderer { s, calendar, millis, _, z ->
            style = s
            seen = calendar
            ts = millis
            zone = z
            "converted"
        }
        val picked = 1_793_059_200_000L // 2026-10-30T00:00Z
        assertEquals(
            "converted",
            pickedDateInGregorian(picked, CalendarSystem.PERSIAN, Locale.ENGLISH, recorder)
        )
        assertEquals(DateStyle.DayWithYear, style)
        assertEquals(
            "the echo exists because the grid is not Gregorian, so it prints Gregorian",
            CalendarSystem.GREGORIAN,
            seen
        )
        assertEquals(ZoneOffset.UTC, zone)
        assertEquals(picked, ts)
    }

    /**
     * Automatic is the default rather than a calendar, so it stays first; the
     * calendars below it follow the alphabet of the label on screen.
     */
    @Test
    fun thePickerIsAutomaticThenAlphabeticalByLabel() {
        val english = mapOf(
            CalendarSystem.AUTO to "Automatic (by language)",
            CalendarSystem.GREGORIAN to "Gregorian",
            CalendarSystem.PERSIAN to "Persian (Jalali)",
            CalendarSystem.HIJRI_UMALQURA to "Islamic (Umm al-Qura)",
            CalendarSystem.HEBREW to "Hebrew",
            CalendarSystem.BUDDHIST to "Buddhist",
            CalendarSystem.JAPANESE to "Japanese",
            CalendarSystem.INDIAN to "Indian (Saka)",
            CalendarSystem.COPTIC to "Coptic"
        )
        assertEquals(
            listOf(
                CalendarSystem.AUTO,
                CalendarSystem.BUDDHIST,
                CalendarSystem.COPTIC,
                CalendarSystem.GREGORIAN,
                CalendarSystem.HEBREW,
                CalendarSystem.INDIAN,
                CalendarSystem.HIJRI_UMALQURA,
                CalendarSystem.JAPANESE,
                CalendarSystem.PERSIAN
            ),
            calendarOptions(Locale.ENGLISH) { english.getValue(it) }
        )
    }

    /** The order follows the label, not the enum: French moves them around. */
    @Test
    fun thePickerOrderFollowsTheLanguage() {
        val french = mapOf(
            CalendarSystem.AUTO to "Automatique (selon la langue)",
            CalendarSystem.GREGORIAN to "Grégorien",
            CalendarSystem.PERSIAN to "Persan (Jalali)",
            CalendarSystem.HIJRI_UMALQURA to "Islamique (Umm al-Qura)",
            CalendarSystem.HEBREW to "Hébreu",
            CalendarSystem.BUDDHIST to "Bouddhiste",
            CalendarSystem.JAPANESE to "Japonais",
            CalendarSystem.INDIAN to "Indien (Saka)",
            CalendarSystem.COPTIC to "Copte"
        )
        val order = calendarOptions(Locale.FRENCH) { french.getValue(it) }
        assertEquals(CalendarSystem.AUTO, order.first())
        assertEquals(
            listOf(
                CalendarSystem.BUDDHIST,
                CalendarSystem.COPTIC,
                CalendarSystem.GREGORIAN,
                CalendarSystem.HEBREW,
                CalendarSystem.INDIAN,
                CalendarSystem.HIJRI_UMALQURA,
                CalendarSystem.JAPANESE,
                CalendarSystem.PERSIAN
            ),
            order.drop(1)
        )
    }
}
