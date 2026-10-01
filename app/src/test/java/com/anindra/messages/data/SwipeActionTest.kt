package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SwipeActionTest {
    @Test
    fun storageValuesAreStable() {
        // These are persisted ints; renumbering would silently repoint an
        // existing user's configuration at a different action.
        assertEquals(0, SwipeAction.OFF.storageValue)
        assertEquals(1, SwipeAction.ARCHIVE.storageValue)
        assertEquals(2, SwipeAction.DELETE.storageValue)
        assertEquals(3, SwipeAction.MARK_READ_UNREAD.storageValue)
        assertEquals(4, SwipeAction.PIN.storageValue)
        assertEquals(5, SwipeAction.BLOCK.storageValue)
    }

    @Test
    fun everyStorageValueRoundTrips() {
        SwipeAction.entries.forEach { action ->
            assertEquals(action, SwipeAction.fromStorage(action.storageValue))
        }
    }

    @Test
    fun unknownStorageValueFallsBackToArchive() {
        assertEquals(SwipeAction.ARCHIVE, SwipeAction.fromStorage(-1))
        assertEquals(SwipeAction.ARCHIVE, SwipeAction.fromStorage(99))
    }

    @Test
    fun legacyEnabledFalseDisablesBothDirections() {
        assertEquals(SwipeAction.OFF to SwipeAction.OFF, SwipeAction.legacyPair(enabled = false, reverse = false))
        assertEquals(SwipeAction.OFF to SwipeAction.OFF, SwipeAction.legacyPair(enabled = false, reverse = true))
    }

    @Test
    fun legacyDefaultIsArchiveLeftDeleteRight() {
        assertEquals(
            SwipeAction.ARCHIVE to SwipeAction.DELETE,
            SwipeAction.legacyPair(enabled = true, reverse = false)
        )
    }

    @Test
    fun legacyReverseSwapsThePair() {
        assertEquals(
            SwipeAction.DELETE to SwipeAction.ARCHIVE,
            SwipeAction.legacyPair(enabled = true, reverse = true)
        )
    }

    @Test
    fun legacyMigrationIsReversibleBetweenTheTwoOrderings() {
        val forward = SwipeAction.legacyPair(enabled = true, reverse = false)
        val reversed = SwipeAction.legacyPair(enabled = true, reverse = true)
        assertNotEquals(forward.first, reversed.first)
        assertEquals(forward.second, reversed.first)
        assertEquals(forward.first, reversed.second)
    }

    @Test
    fun offIsDistinctFromEveryRealAction() {
        // OFF must be the only value that means "not swipeable".
        SwipeAction.entries.filter { it != SwipeAction.OFF }
            .forEach { assertNotEquals(SwipeAction.OFF, it) }
    }
}
