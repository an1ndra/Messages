package com.anindra.messages.ui

/**
 * The per-session reveal cache behind message locking. Kept out of the
 * composable so the lock/unlock transitions are unit tested, and so the render
 * path and the mutations share one rule: a locked message hides its body unless
 * its id is in [unlockedIds].
 */
object MessageLockState {
    /** A locked message hides its body until it is unlocked for this session. */
    fun isHidden(locked: Boolean, id: Long, unlockedIds: Set<Long>): Boolean =
        locked && id !in unlockedIds

    /** Locking re-hides: a session reveal must not outlive the lock. */
    fun onLock(unlockedIds: Set<Long>, ids: Collection<Long>): Set<Long> =
        unlockedIds - ids.toSet()

    /** Unlocking reveals these messages for the rest of the chat session. */
    fun onUnlock(unlockedIds: Set<Long>, ids: Collection<Long>): Set<Long> =
        unlockedIds + ids
}
