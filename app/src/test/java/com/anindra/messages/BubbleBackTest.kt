package com.anindra.messages

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #354 follow-up: the reporter asked that Back inside a bubble not close the
 * bubble, "disabled by default, i mean no closing bubble as default behavior".
 *
 * Back used to `finish()` the host whenever the window was a bubble, which
 * cancelled its notification and took the bubble with it. It now only does that
 * when the new setting says so; otherwise it shows the chat list and leaves the
 * bubble up.
 */
class BubbleBackTest {

    private fun src(relative: String): String =
        File(LocaleCatalog.repoRoot(), "app/src/main/$relative").readText()

    @Test
    fun backOnlyClosesTheBubbleWhenTheSettingIsOn() {
        assertEquals(BubbleBack.SHOW_LIST, bubbleBackAction(bubbleMode = true, backClosesBubble = false))
        assertEquals(BubbleBack.FINISH, bubbleBackAction(bubbleMode = true, backClosesBubble = true))
        // Outside a bubble the setting is irrelevant: Back never finishes the app.
        assertEquals(BubbleBack.SHOW_LIST, bubbleBackAction(bubbleMode = false, backClosesBubble = false))
        assertEquals(BubbleBack.SHOW_LIST, bubbleBackAction(bubbleMode = false, backClosesBubble = true))
    }

    @Test
    fun theSettingDefaultsToNotClosing() {
        val store = src("java/com/anindra/messages/data/SettingsStore.kt")
        assertTrue(
            "the setting key is gone",
            store.contains("KEY_BUBBLE_BACK_CLOSES")
        )
        assertTrue(
            "the setting defaults to closing the bubble, the opposite of the request",
            store.contains("prefs.getBoolean(KEY_BUBBLE_BACK_CLOSES, false)")
        )
    }

    @Test
    fun theChatBackPathAsksTheDecisionInsteadOfFinishing() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "the bubble's Back still finishes unconditionally",
            activity.contains("bubbleBackAction(bubbleMode, vm.settings.bubbleBackCloses)")
        )
        assertTrue(
            "the setting is not offered in the new settings UI",
            src("java/com/anindra/messages/ui/AdvancedSubScreens.kt")
                .contains("settings_bubble_back_closes_title")
        )
        assertTrue(
            "the setting is not offered in the legacy settings UI",
            src("java/com/anindra/messages/ui/legacy/LegacyAdvancedSettingsScreen.kt")
                .contains("settings_bubble_back_closes_title")
        )
    }
}
