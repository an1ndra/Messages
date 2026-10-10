package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lock -> unlock -> lock must end hidden. The session reveal used to survive a
 * re-lock, so the body stayed readable while the UI still reported the message
 * as locked.
 */
class MessageLockStateTest {
    @Test
    fun lockUnlockLockEndsHidden() {
        val id = 42L
        var revealed = emptySet<Long>()

        assertTrue(MessageLockState.isHidden(locked = true, id = id, revealed))

        revealed = MessageLockState.onUnlock(revealed, listOf(id))
        assertFalse(MessageLockState.isHidden(locked = true, id = id, revealed))

        revealed = MessageLockState.onLock(revealed, listOf(id))
        assertTrue(MessageLockState.isHidden(locked = true, id = id, revealed))
    }

    @Test
    fun onLockRemovesOnlyTheTargets() {
        assertEquals(
            setOf(1L),
            MessageLockState.onLock(setOf(1L, 2L, 3L), listOf(2L, 3L))
        )
    }

    @Test
    fun onUnlockRevealsOnlyTheTargets() {
        assertEquals(
            setOf(1L, 2L),
            MessageLockState.onUnlock(setOf(1L), listOf(2L))
        )
    }

    @Test
    fun unlockedMessageIsNeverHidden() {
        assertFalse(MessageLockState.isHidden(locked = false, id = 7L, emptySet()))
    }
}
