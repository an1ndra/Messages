package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeGuardTest {

    @Test
    fun anHonestReportStands() {
        assertEquals(800, ImeGuard.appliedBottomPx(reportedPx = 800, visibleOverlapPx = 800))
    }

    @Test
    fun aReportSmallerThanTheOverlapStandsToo() {
        // A device that resized the window for the keyboard reports less than
        // the screen-wide overlap; trusting the report here is what keeps the
        // composer on the keyboard there.
        assertEquals(300, ImeGuard.appliedBottomPx(reportedPx = 300, visibleOverlapPx = 800))
    }

    @Test
    fun aDishonestReportIsClampedToWhatTheWindowCanShow() {
        // MIUI with an enlarged display size reports nearly the whole screen.
        assertEquals(
            670,
            ImeGuard.appliedBottomPx(reportedPx = 1507, visibleOverlapPx = 670)
        )
    }

    @Test
    fun aNegativeOverlapMeansNoKeyboardToShow() {
        assertEquals(0, ImeGuard.appliedBottomPx(reportedPx = 1507, visibleOverlapPx = -5))
        assertEquals(0, ImeGuard.appliedBottomPx(reportedPx = 0, visibleOverlapPx = -5))
    }

    @Test
    fun theOverlapIsTheWindowBottomAgainstTheVisibleFrame() {
        // Full-screen window, keyboard over the bottom third.
        assertEquals(800, ImeGuard.overlapPx(0, 2400, 1600))
        // Nothing covered.
        assertEquals(0, ImeGuard.overlapPx(0, 2400, 2400))
        // A window that does not start at the top of the screen still measures
        // from its own bottom edge.
        assertEquals(300, ImeGuard.overlapPx(300, 930, 930))
    }

    @Test
    fun aDebugStandInReplacesTheReportUntilItIsCleared() {
        val previous = ImeGuard.debugReportedBottomPx
        try {
            ImeGuard.debugReportedBottomPx = null
            assertEquals(640, ImeGuard.reportedBottomPx(640))
            ImeGuard.debugReportedBottomPx = 2200
            assertEquals(2200, ImeGuard.reportedBottomPx(640))
        } finally {
            ImeGuard.debugReportedBottomPx = previous
        }
    }
}
