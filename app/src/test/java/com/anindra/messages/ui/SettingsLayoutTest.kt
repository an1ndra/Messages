package com.anindra.messages.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutTest {
    @Test
    fun outerCornersSoftenAndInnerCornersStaySquare() {
        val first = rowCorners(RowPosition.FIRST)
        assertEquals(SettingsLayout.OUTER_RADIUS, first.topStart)
        assertEquals(SettingsLayout.OUTER_RADIUS, first.topEnd)
        assertEquals(SettingsLayout.INNER_RADIUS, first.bottomStart)
        assertEquals(SettingsLayout.INNER_RADIUS, first.bottomEnd)

        val last = rowCorners(RowPosition.LAST)
        assertEquals(SettingsLayout.INNER_RADIUS, last.topStart)
        assertEquals(SettingsLayout.OUTER_RADIUS, last.bottomEnd)
        assertEquals(SettingsLayout.OUTER_RADIUS, last.bottomStart)
    }

    @Test
    fun middleRowsAreMoreRectangularThanTheEnds() {
        val middle = rowCorners(RowPosition.MIDDLE)
        listOf(middle.topStart, middle.topEnd, middle.bottomEnd, middle.bottomStart)
            .forEach { assertEquals(SettingsLayout.INNER_RADIUS, it) }
        assertTrue(SettingsLayout.INNER_RADIUS < SettingsLayout.OUTER_RADIUS)
    }

    @Test
    fun theOuterRadiusStaysShortOfAPill() {
        // The group's two ends are clearly rounded, but a pill radius on every
        // stacked row would turn the list into a column of chips.
        assertTrue(SettingsLayout.OUTER_RADIUS <= 14.dp)
        assertTrue(SettingsLayout.INNER_RADIUS * 2 < SettingsLayout.OUTER_RADIUS)
    }

    @Test
    fun aLoneRowIsRoundedOnEveryCorner() {
        val single = rowCorners(RowPosition.SINGLE)
        listOf(single.topStart, single.topEnd, single.bottomEnd, single.bottomStart)
            .forEach { assertEquals(SettingsLayout.OUTER_RADIUS, it) }
    }

    @Test
    fun rowsAreSeparatedByASmallGap() {
        assertTrue(SettingsLayout.ROW_GAP > 0.dp)
        // A tight gap keeps the run reading as one group rather than a loose list.
        assertTrue(SettingsLayout.ROW_GAP <= 3.dp)
    }

    @Test
    fun groupGapIsWiderThanRowGapSoSectionsSeparate() {
        assertTrue(SettingsLayout.GROUP_GAP > SettingsLayout.ROW_GAP)
    }

    @Test
    fun topGapIsTighterThanTheGapBetweenGroups() {
        // Below the app bar the gap is spacing from the title, not a section
        // break, so reusing the group size leaves an empty band under the bar.
        assertTrue(SettingsLayout.TOP_GAP < SettingsLayout.GROUP_GAP)
    }

    @Test
    fun aTitleOnlyRowIsShorterThanOneWithADescription() {
        val titleOnly = SettingsLayout.rowMinHeight(hasSubtitle = false, largeTouchTargets = false)
        val withSubtitle = SettingsLayout.rowMinHeight(hasSubtitle = true, largeTouchTargets = false)
        assertTrue("$titleOnly should be shorter than $withSubtitle", titleOnly < withSubtitle)
    }

    @Test
    fun everyRowVariantClearsTheMinimumTouchTarget() {
        for (hasSubtitle in listOf(true, false)) {
            for (large in listOf(true, false)) {
                val h = SettingsLayout.rowMinHeight(hasSubtitle, large)
                assertTrue("$h (subtitle=$hasSubtitle, large=$large) must clear 48dp", h >= A11y.MIN_TOUCH_DP.dp)
            }
        }
    }

    @Test
    fun largeTouchTargetsGrowEveryVariant() {
        for (hasSubtitle in listOf(true, false)) {
            assertTrue(
                SettingsLayout.rowMinHeight(hasSubtitle, largeTouchTargets = true) >
                    SettingsLayout.rowMinHeight(hasSubtitle, largeTouchTargets = false)
            )
        }
    }

    @Test
    fun textSitsInsetFromTheCardEdge() {
        assertTrue(SettingsLayout.ROW_CONTENT_PADDING > SettingsLayout.SCREEN_PADDING)
    }

    @Test
    fun layoutValuesAreStable() {
        assertEquals(12.dp, SettingsLayout.SCREEN_PADDING)
        assertEquals(2.dp, SettingsLayout.ROW_GAP)
        assertEquals(14.dp, SettingsLayout.GROUP_GAP)
        assertEquals(8.dp, SettingsLayout.TOP_GAP)
        assertEquals(18.dp, SettingsLayout.ROW_CONTENT_PADDING)
        assertEquals(12.dp, SettingsLayout.OUTER_RADIUS)
        assertEquals(4.dp, SettingsLayout.INNER_RADIUS)
        assertEquals(56.dp, SettingsLayout.ROW_HEIGHT_TITLE_ONLY)
        assertEquals(76.dp, SettingsLayout.ROW_HEIGHT_WITH_SUBTITLE)
        assertEquals(12.dp, SettingsLayout.LARGE_TARGET_GROWTH)
        assertEquals(6.dp, SettingsLayout.FOOTER_TOP_GAP)
        assertEquals(20.dp, SettingsLayout.FOOTER_BOTTOM_GAP)
    }

    @Test
    fun footerSitsTightAgainstTheGroupItDescribes() {
        // A full section gap would make the note read as the start of a new
        // section rather than as a caption on the group above it.
        assertTrue(SettingsLayout.FOOTER_TOP_GAP < SettingsLayout.GROUP_GAP)
    }

    @Test
    fun footerBottomGapOnlyClearsTheSystemBar() {
        // It is below the last row, so it needs no more than the nav bar, and a
        // large value leaves the note floating in the middle of the page. Spelled
        // out in absolute terms because the row gap is only 2dp.
        assertTrue(SettingsLayout.FOOTER_BOTTOM_GAP <= 32.dp)
        assertTrue(SettingsLayout.FOOTER_BOTTOM_GAP > 0.dp)
    }
}
