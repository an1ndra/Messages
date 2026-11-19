package com.anindra.messages.data

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #354 follow-up: "too many fingerprint interrupts ... maybe unlock once until
 * screen lock can be good".
 *
 * The app-lock prompt lived in `setContent`, so every activity creation asked
 * again — cold start, share, screen rotation, and each bubble open. The unlock
 * now lasts for the session, and the session ends when the screen does.
 */
class LockSessionTest {

    private fun src(relative: String): String =
        File(LocaleCatalog.repoRoot(), "app/src/main/$relative").readText()

    @Test
    fun theSessionStartsLockedAndEndsOnLock() {
        LockSession.clear()
        assertFalse("a fresh session is unlocked", LockSession.isUnlocked())
        LockSession.grant()
        assertTrue("unlock did not stick", LockSession.isUnlocked())
        LockSession.clear()
        assertFalse("lock did not end the session", LockSession.isUnlocked())
    }

    @Test
    fun thePromptIsSkippedForAnAlreadyUnlockedSession() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "the app-lock gate no longer consults the session",
            activity.contains("LockSession.isUnlocked()")
        )
        assertTrue(
            "a successful prompt does not open the session",
            activity.contains("com.anindra.messages.data.LockSession.grant()")
        )
    }

    @Test
    fun theSessionEndsWhenTheScreenGoesOff() {
        val app = src("java/com/anindra/messages/MessagesApplication.kt")
        assertTrue(
            "nothing listens for the screen turning off",
            app.contains("Intent.ACTION_SCREEN_OFF")
        )
        assertTrue(
            "screen off does not end the unlocked session",
            app.contains("LockSession.clear()")
        )
    }

    @Test
    fun theSessionIsNotNamedLikeALock() {
        // `lock()`/`unlock()` read as a critical section to anything that looks
        // for one, and CodeQL reported the probe's lock() as an unreleased lock
        // on a flag that is meant to stay set. The names say what they are.
        val source = File("app/src/main/java/com/anindra/messages/data/LockSession.kt")
            .takeIf { it.exists() }
            ?.readText()
            ?: File("../app/src/main/java/com/anindra/messages/data/LockSession.kt").readText()
        assertTrue(source.contains("fun grant()"))
        assertTrue(source.contains("fun clear()"))
        assertFalse(source.contains("fun lock()"))
        assertFalse(source.contains("fun unlock()"))
    }
}
