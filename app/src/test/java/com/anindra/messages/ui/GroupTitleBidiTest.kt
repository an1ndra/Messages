package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A group title is a list of people, and in an RTL paragraph a phone number in
 * it used to break: the leading '+' is a neutral character, so "+12345678,
 * +12345789" painted as "8765431+, 98754321+" — both numbers broken and the
 * order reversed. Reported against the group MMS work (#448).
 *
 * The transform only adds Unicode isolates; the words never change.
 */
class GroupTitleBidiTest {

    private val lri = "\u2066"
    private val rli = "\u2067"
    private val pdi = "\u2069"

    @Test
    fun aNumberListKeepsEveryNumberIntactAndInOrder() {
        val title = "+12345678, +12345789"
        val painted = BidiText.groupTitle(title)
        assertEquals("$lri$lri+12345678$pdi, $lri+12345789$pdi$pdi", painted)
        assertTrue("the first number must come first", painted.indexOf("12345678") < painted.indexOf("12345789"))
    }

    @Test
    fun aSingleNumberIsIsolatedOnce() {
        assertEquals("${lri}+15551234567$pdi", BidiText.groupTitle("+15551234567"))
    }

    @Test
    fun persianNamesKeepRightToLeftOrder() {
        val title = "علی, رضا"
        val painted = BidiText.groupTitle(title)
        assertEquals("${rli}علی$pdi, ${rli}رضا$pdi", painted)
        assertFalse("an RTL list must not be pinned left to right", painted.startsWith(lri))
    }

    @Test
    fun aMixedListIsolatesTheNumbersAndLeavesTheOrderToTheParagraph() {
        val title = "علی, +12345678"
        val painted = BidiText.groupTitle(title)
        assertTrue(painted.contains("$lri+12345678$pdi"))
        assertTrue(painted.contains("${rli}علی$pdi"))
        assertFalse("a mixed list must not be pinned left to right", painted.startsWith(lri))
    }

    @Test
    fun latinNamesArePinnedLikeNumbers() {
        val painted = BidiText.groupTitle("Alex, Sam")
        assertEquals("$lri${lri}Alex$pdi, ${lri}Sam$pdi$pdi", painted)
    }

    @Test
    fun theTransformIsIdempotent() {
        val once = BidiText.groupTitle("+12345678, +12345789")
        assertEquals(once, BidiText.groupTitle(once))
    }

    @Test
    fun aBlankTitleIsLeftAlone() {
        assertEquals("", BidiText.groupTitle(""))
        assertEquals("   ", BidiText.groupTitle("   "))
    }

    @Test
    fun aUserTypedNameIsNotRewritten() {
        // The words are untouched: only isolates are added around them.
        val painted = BidiText.groupTitle("Weekend Trip")
        assertEquals("${lri}Weekend Trip$pdi", painted)
    }
}
