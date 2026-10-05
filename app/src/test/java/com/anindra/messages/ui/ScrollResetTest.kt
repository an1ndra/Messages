package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings is reached from the conversation list and from its own sub-screens.
 * The two arrivals want opposite scroll behaviour, and conflating them either
 * re-breaks issue #265 or leaves Settings opening mid-list every time.
 */
class ScrollResetTest {

    @Test
    fun enteringSettingsFromTheListStartsAtTheTop() {
        assertTrue(ScrollReset.shouldResetToTop(fromRoute = "list", toRoute = "settings"))
    }

    @Test
    fun returningFromASubScreenKeepsThePosition() {
        // Issue #265: Accessibility was opened from a row further down the list,
        // so returning to the top lost the user's place.
        for (from in listOf("advanced", "accessibility", "inbox", "trash", "spam", "scheduled")) {
            assertFalse(from, ScrollReset.shouldResetToTop(from, "settings"))
        }
    }

    @Test
    fun leavingSettingsDoesNotResetIt() {
        assertFalse(ScrollReset.shouldResetToTop(fromRoute = "settings", toRoute = "list"))
        assertFalse(ScrollReset.shouldResetToTop(fromRoute = "settings", toRoute = "advanced"))
    }

    @Test
    fun aColdStartOnSettingsIsNotTreatedAsArrivingFromTheList() {
        // The remembered previous route starts at "list", which would reset the
        // scroll on launch even though the user never navigated here.
        assertFalse(ScrollReset.shouldResetToTop(fromRoute = null, toRoute = "settings"))
    }

    @Test
    fun otherDestinationsAreNeverReset() {
        assertFalse(ScrollReset.shouldResetToTop(fromRoute = "list", toRoute = "chat"))
        assertFalse(ScrollReset.shouldResetToTop(fromRoute = "list", toRoute = "advanced"))
        assertFalse(ScrollReset.shouldResetToTop(fromRoute = "list", toRoute = "trash"))
    }
}