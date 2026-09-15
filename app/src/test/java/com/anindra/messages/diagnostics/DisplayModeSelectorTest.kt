package com.anindra.messages.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayModeSelectorTest {

    @Test
    fun picksHighestRefreshAtTheSameResolution() {
        val current = DisplayModeInfo(1, 1080, 2400, 60f)
        val modes = listOf(
            DisplayModeInfo(1, 1080, 2400, 60f),
            DisplayModeInfo(2, 1080, 2400, 120f),
            DisplayModeInfo(3, 1440, 3120, 120f)
        )
        assertEquals(2, DisplayModeSelector.bestModeId(current, modes))
    }

    @Test
    fun neverSwitchesToADifferentResolution() {
        val current = DisplayModeInfo(1, 1080, 2400, 60f)
        val modes = listOf(
            DisplayModeInfo(1, 1080, 2400, 60f),
            DisplayModeInfo(3, 1440, 3120, 120f)
        )
        assertNull(DisplayModeSelector.bestModeId(current, modes))
    }

    @Test
    fun nullWhenCurrentIsAlreadyTheBest() {
        val current = DisplayModeInfo(2, 1080, 2400, 120f)
        val modes = listOf(
            DisplayModeInfo(1, 1080, 2400, 60f),
            DisplayModeInfo(2, 1080, 2400, 120f)
        )
        assertNull(DisplayModeSelector.bestModeId(current, modes))
    }

    @Test
    fun nullWhenCurrentOrModesUnknown() {
        assertNull(DisplayModeSelector.bestModeId(null, listOf(DisplayModeInfo(1, 1080, 2400, 120f))))
        assertNull(DisplayModeSelector.bestModeId(DisplayModeInfo(1, 1080, 2400, 60f), emptyList()))
    }
}
