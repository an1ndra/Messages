package com.anindra.messages.data

/**
 * How long one successful app-lock check lasts (#354 follow-up).
 *
 * The prompt used to run in `setContent`, so every activity the app created
 * asked again: each cold start, each share, and — after bubbles landed — each
 * time a bubble opened. The reporter called it "too many fingerprint
 * interrupts" and suggested unlocking "once until screen lock", which is also
 * where the security boundary actually is: once the screen is off, the phone is
 * out of the owner's hands again.
 *
 * Process-wide and deliberately not persisted: a process restart re-asks, so a
 * kill of the app never leaves it unlocked on disk.
 */
object LockSession {

    @Volatile
    private var unlocked = false

    /**
     * Windows showing content behind the gate, told when the session ends.
     *
     * Re-reading the session on `ON_RESUME` missed a lock delivered while the
     * window was already resumed (a delayed screen-off broadcast, or a bubble
     * that the platform keeps resumed): no resume event followed, so the chat
     * stayed readable. Observation does not depend on that ordering (#354).
     */
    private val observers = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    fun isUnlocked(): Boolean = unlocked

    fun unlock() {
        if (unlocked) return
        unlocked = true
        notifyObservers()
    }

    /** Called when the screen goes off, and by anything that invalidates the session. */
    fun lock() {
        if (!unlocked) return
        unlocked = false
        notifyObservers()
    }

    fun addObserver(observer: () -> Unit) {
        observers.add(observer)
    }

    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
    }

    private fun notifyObservers() {
        for (observer in observers) {
            try {
                observer()
            } catch (t: Throwable) {
                android.util.Log.w("LockSession", "observer failed: $t")
            }
        }
    }
}

/**
 * Whether a window may show its content right now (#354 follow-up).
 *
 * Read when a window is built, and again on every session change it observes,
 * so the rule lives in one place instead of being re-derived at the second call
 * site. The second read is what re-arms a window the session ended underneath:
 * `MessagesApplication` locks on `ACTION_SCREEN_OFF`, and an activity that is
 * already on screen is never rebuilt by that, so nothing else would notice.
 */
object AppLockGate {

    fun allowsContent(appLockEnabled: Boolean, sessionUnlocked: Boolean): Boolean =
        !appLockEnabled || sessionUnlocked
}
