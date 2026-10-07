package com.anindra.messages.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class A11yTest {

    @Test
    fun touchTargetNeverShrinksBelowTheMinimum() {
        assertEquals(A11y.MIN_TOUCH_DP.dp, A11y.touchTarget(32.dp))
        assertEquals(A11y.MIN_TOUCH_DP.dp, A11y.touchTarget(47.9f.dp))
        assertEquals(A11y.MIN_TOUCH_DP.dp, A11y.touchTarget(48.dp))
    }

    @Test
    fun touchTargetKeepsLargerSizes() {
        assertEquals(56.dp, A11y.touchTarget(56.dp))
    }

    @Test
    fun describeJoinsNonBlankPartsWithAPeriod() {
        assertEquals("Ann. Hello. 10:00", A11y.describe("Ann", "Hello", "10:00"))
    }

    @Test
    fun describeDropsNullAndBlankParts() {
        assertEquals("Ann. Hello", A11y.describe("Ann", "  ", null, "", "Hello"))
        assertEquals("", A11y.describe(null, " ", ""))
    }

    @Test
    fun searchResultAnnouncementKeepsTheReadableBody() {
        // The marker used to *replace* the bubble's readable text, so a focused
        // hit announced nothing but "Search result".
        assertEquals("see the code. Search result", A11y.describe("see the code", "Search result"))
    }

    @Test
    fun everyOptionIsListedExactlyOnce() {
        assertEquals(A11yOption.entries.toSet(), A11Y_OPTION_ROWS.map { it.option }.toSet())
        assertEquals(A11yOption.entries.size, A11Y_OPTION_ROWS.size)
    }

    @Test
    fun theOptionsFormOneGroupWithRoundedEnds() {
        // A single group means the run starts and ends there: the first row
        // softens its top corners, the last its bottom ones, and nothing in
        // between is treated as the end of a section.
        assertEquals(RowPosition.FIRST, A11Y_OPTION_ROWS.first().position)
        assertEquals(RowPosition.LAST, A11Y_OPTION_ROWS.last().position)
        assertTrue(A11Y_OPTION_ROWS.drop(1).dropLast(1).all { it.position == RowPosition.MIDDLE })
    }

    @Test
    fun fontSizeStaysAtTheTopAndReduceMotionAtTheBottom() {
        assertEquals(A11yOption.FONT_SIZE, A11Y_OPTION_ROWS.first().option)
        assertEquals(A11yOption.REDUCE_MOTION, A11Y_OPTION_ROWS.last().option)
    }
}
