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

    fun isUnlocked(): Boolean = unlocked

    fun unlock() {
        unlocked = true
    }

    /** Called when the screen goes off, and by anything that invalidates the session. */
    fun lock() {
        unlocked = false
    }
}

/**
 * Whether a window may show its content right now (#354 follow-up).
 *
 * Read twice per window — once when it is built, and again on every resume — so
 * the rule lives in one place instead of being re-derived at the second call
 * site. The resume read is what re-arms a window the session ended underneath:
 * `MessagesApplication` locks on `ACTION_SCREEN_OFF`, and an activity that is
 * already on screen is never rebuilt by that, so nothing else would notice.
 */
object AppLockGate {

    fun allowsContent(appLockEnabled: Boolean, sessionUnlocked: Boolean): Boolean =
        !appLockEnabled || sessionUnlocked
}
