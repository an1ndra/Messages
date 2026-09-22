package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectedTabCornersTest {

    private val second = 1

    @Test
    fun activeLeadingTabIsRoundedOnTheLeftOuterEdgeOnly() {
        val r = ConnectedTabCorners.radii(index = 0, lastIndex = second, active = true)
        assertEquals(listOf(24f, 8f, 8f, 24f), r)
    }

    @Test
    fun activeTrailingTabIsRoundedOnTheRightOuterEdgeOnly() {
        val r = ConnectedTabCorners.radii(index = second, lastIndex = second, active = true)
        assertEquals(listOf(8f, 24f, 24f, 8f), r)
    }

    @Test
    fun inactiveTabsStayLessRounded() {
        assertEquals(
            listOf(8f, 8f, 8f, 8f),
            ConnectedTabCorners.radii(index = 0, lastIndex = second, active = false)
        )
        assertEquals(
            listOf(8f, 8f, 8f, 8f),
            ConnectedTabCorners.radii(index = second, lastIndex = second, active = false)
        )
    }

    @Test
    fun middleTabsAreAlwaysLessRounded() {
        assertEquals(
            listOf(8f, 8f, 8f, 8f),
            ConnectedTabCorners.radii(index = 1, lastIndex = 2, active = true)
        )
    }

    @Test
    fun activeRadiusIsLargerThanInactive() {
        assert(ConnectedTabCorners.ACTIVE > ConnectedTabCorners.INACTIVE)
    }
}
