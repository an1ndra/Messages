package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "back to top" shortcut is only worth a button once the top is far enough
 * away that scrolling back is a chore: a list one flick from the top gets a
 * shortcut it does not need, sitting on the rows.
 */
class BackToTopTest {

    @Test
    fun aListAtTheTopOffersNoShortcut() {
        assertFalse(BackToTop.visible(firstVisibleIndex = 0))
    }

    @Test
    fun aListAFewRowsDownOffersNoShortcut() {
        assertFalse(BackToTop.visible(firstVisibleIndex = BackToTop.THRESHOLD - 1))
    }

    @Test
    fun theButtonIsDrawnAtSeventyPercent() {
        // It floats over the rows in the middle of the screen: fully opaque it
        // reads as a hole punched through the list.
        assertEquals(0.7f, BackToTop.ALPHA, 0.001f)
    }

    @Test
    fun aListScrolledPastTheThresholdOffersOne() {
        assertTrue(BackToTop.visible(firstVisibleIndex = BackToTop.THRESHOLD))
        assertTrue(BackToTop.visible(firstVisibleIndex = 40))
    }
}
