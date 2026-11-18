package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFiltersTest {

    private fun parse(query: String) = SearchFilters.parse(query).filters

    @Test
    fun aQueryWithoutOperatorsIsAllPlainText() {
        val f = parse("hello world")
        assertNull(f.from)
        assertFalse(f.hasAttachment)
        assertNull(f.before)
        assertNull(f.after)
        assertEquals("hello world", f.plainText)
    }

    @Test
    fun fromTakesOneTokenOrAQuotedValue() {
        assertEquals("Dad", parse("from:Dad hello").from)
        assertEquals("Kami 0910", parse("from:\"Kami 0910\" hello").from)
        assertEquals("hello", parse("from:\"Kami 0910\" hello").plainText)
        assertEquals("Dad", parse("FROM:Dad").from)
    }

    @Test
    fun anOperatorWithoutAValueStaysPlainText() {
        val f = parse("from: Dad")
        assertNull(f.from)
        assertEquals("from: Dad", f.plainText)
    }

    @Test
    fun hasAttachmentIsASingleWholeToken() {
        assertTrue(parse("has:attachment").hasAttachment)
        assertFalse(parse("has:attachments").hasAttachment)
        assertEquals("has:attachments", parse("has:attachments").plainText)
        assertTrue(parse("snow has:Attachment report").hasAttachment)
        assertEquals("snow report", parse("snow has:Attachment report").plainText)
    }

    @Test
    fun datesParseInAnyDigitScript() {
        assertEquals(CalendarDayInput(2026, 11, 5), parse("before:2026/11/5").before)
        assertEquals(CalendarDayInput(2026, 11, 5), parse("before:2026-11-05").before)
        assertEquals(
            CalendarDayInput(1405, 8, 5),
            parse("before:\u06F1\u06F4\u06F0\u06F5/\u06F0\u06F8/\u06F0\u06F5").before
        )
    }

    @Test
    fun anInvalidDateStaysPlainTextAndStopsAtTheNextToken() {
        val f = parse("before:foo from:Dad")
        assertNull(f.before)
        assertEquals("Dad", f.from)
        assertEquals("before:foo", f.plainText)
    }

    @Test
    fun dayBoundsOutOfRangeAreNotDates() {
        assertNull(parse("before:2026/13/5").before)
        assertNull(parse("before:2026/11/32").before)
        assertNull(parse("before:11/5").before)
    }

    @Test
    fun operatorsCombine() {
        val f = parse("from:Dad has:attachment before:2026/11/1 recital")
        assertEquals("Dad", f.from)
        assertTrue(f.hasAttachment)
        assertEquals(CalendarDayInput(2026, 11, 1), f.before)
        assertEquals("recital", f.plainText)
        assertTrue(f.hasMessageFilters)
    }

    @Test
    fun aMessageFilterAloneCarriesNoText() {
        val f = parse("has:attachment")
        assertEquals("", f.plainText)
        assertTrue(f.hasMessageFilters)
    }

    @Test
    fun removingAChipDropsOnlyThatOperator() {
        val query = "from:Dad has:attachment before:2026/11/1 recital"
        val parsed = SearchFilters.parse(query)
        assertEquals(
            "from:Dad before:2026/11/1 recital",
            parsed.without(SearchFilters.FilterKind.ATTACHMENTS)
        )
        assertEquals(
            "has:attachment before:2026/11/1 recital",
            parsed.without(SearchFilters.FilterKind.FROM)
        )
        assertEquals(
            "from:Dad has:attachment recital",
            parsed.without(SearchFilters.FilterKind.BEFORE)
        )
    }

    @Test
    fun removingAChipKeepsQuotedValuesIntact() {
        val query = "snow from:\"Kami 0910\" after:2026/11/1"
        val parsed = SearchFilters.parse(query)
        assertEquals("snow after:2026/11/1", parsed.without(SearchFilters.FilterKind.FROM))
        assertEquals("snow from:\"Kami 0910\"", parsed.without(SearchFilters.FilterKind.AFTER))
    }

    @Test
    fun removingWhatIsNotThereReturnsTheOriginal() {
        val parsed = SearchFilters.parse("hello")
        assertEquals("hello", parsed.without(SearchFilters.FilterKind.FROM))
    }

    @Test
    fun doubleSpacesDoNotSurviveARemoval() {
        val parsed = SearchFilters.parse("has:attachment snow")
        assertEquals("snow", parsed.without(SearchFilters.FilterKind.ATTACHMENTS))
    }
}
