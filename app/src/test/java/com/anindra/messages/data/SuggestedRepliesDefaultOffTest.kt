package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The one-tap replies (#483) are opt-in: an install that never touched the
 * switch must not put chips above the composer, because a wrong suggestion is
 * worse than none. The switch stays, and it still owns both surfaces.
 */
class SuggestedRepliesDefaultOffTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val store get() = read("src/main/java/com/anindra/messages/data/SettingsStore.kt")
    private val chat get() = read("src/main/java/com/anindra/messages/ui/ChatScreen.kt")
    private val notifier get() = read("src/main/java/com/anindra/messages/sms/SmsSupport.kt")
    private val settings get() = read("src/main/java/com/anindra/messages/ui/AdvancedSubScreens.kt")

    @Test
    fun anUntouchedInstallSuggestsNothing() {
        assertTrue(
            "the chips must start off, or every fresh install gets them",
            store.contains("prefs.getBoolean(KEY_SUGGESTED_REPLIES, false)")
        )
        assertFalse(
            "an on-by-default read is the bug this pins",
            store.contains("prefs.getBoolean(KEY_SUGGESTED_REPLIES, true)")
        )
    }

    @Test
    fun theSwitchStillOwnsTheComposerChips() {
        assertTrue(
            chat.contains("SuggestedReplies.forThread(messages, vm.settings.suggestedRepliesEnabled)")
        )
    }

    @Test
    fun theSwitchStillOwnsTheNotificationActions() {
        assertTrue(
            notifier.contains("if (!app.repository.settings.suggestedRepliesEnabled) emptyArray()")
        )
    }

    @Test
    fun theSwitchIsStillReachable() {
        assertTrue(
            "opting in needs a row to tick",
            settings.contains("R.string.settings_suggested_replies_title")
        )
        assertTrue(
            "and the row has to write the same flag the chips read",
            settings.contains("vm.settings.suggestedRepliesEnabled = it")
        )
    }
}
