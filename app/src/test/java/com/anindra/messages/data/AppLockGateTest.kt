package com.anindra.messages.data

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #354 follow-up, second round: "the bubble of a conversation, when it opens
 * once, won't get locked on screen lock".
 *
 * `LockSession` already ended on `ACTION_SCREEN_OFF`, but a window only read it
 * once, when it was built. An open bubble was therefore never rebuilt by the
 * lock and kept showing its chat. The gate now re-reads the same session on
 * resume; these cases pin the rule itself and the wiring that applies it.
 */
class AppLockGateTest {

    private fun src(relative: String): String =
        File(LocaleCatalog.repoRoot(), "app/src/main/$relative").readText()

    @Test
    fun contentNeedsNoSessionWhenTheLockIsOff() {
        assertTrue(AppLockGate.allowsContent(appLockEnabled = false, sessionUnlocked = false))
        assertTrue(AppLockGate.allowsContent(appLockEnabled = false, sessionUnlocked = true))
    }

    @Test
    fun contentNeedsAnUnlockedSessionWhenTheLockIsOn() {
        assertTrue(AppLockGate.allowsContent(appLockEnabled = true, sessionUnlocked = true))
        assertFalse(AppLockGate.allowsContent(appLockEnabled = true, sessionUnlocked = false))
    }

    @Test
    fun aWindowBuiltForAnUnlockedSessionStartsUnlocked() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "the gate is re-derived at the call site instead of using AppLockGate.allowsContent",
            activity.contains("AppLockGate.allowsContent(")
        )
    }

    @Test
    fun anOpenWindowRechecksTheSessionWhenItComesBack() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "nothing re-reads the session when the window resumes",
            activity.contains("Lifecycle.Event.ON_RESUME")
        )
        assertTrue(
            "the resumed window does not fall back into the locked state",
            Regex("appUnlocked\\s*=\\s*false").containsMatchIn(activity)
        )
    }

    @Test
    fun theBubbleWindowInheritsTheGate() {
        val bubble = src("java/com/anindra/messages/BubbleChatActivity.kt")
        assertTrue(
            "BubbleChatActivity stopped extending MainActivity, so bubbles would skip the re-check",
            bubble.contains(": MainActivity()")
        )
    }
}
