package com.anindra.messages

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #354 follow-up: the reporter asked that Back inside a bubble not close the
 * bubble, "disabled by default, i mean no closing bubble as default behavior".
 *
 * Back used to `finish()` the host whenever the window was a bubble, which
 * cancelled its notification and took the bubble with it. It now only does that
 * when the new setting says so. Otherwise the platform collapses the expanded
 * bubble: from Android 12 a bubble's chat runs in a task of its own, and
 * SystemUI collapses the bubble when Back reaches the task root. Consuming the
 * gesture in the app made it do nothing (#441).
 */
class BubbleBackTest {

    private fun src(relative: String): String =
        File(LocaleCatalog.repoRoot(), "app/src/main/$relative").readText()

    @Test
    fun backOnlyClosesTheBubbleWhenTheSettingIsOn() {
        assertEquals(BubbleBack.STAY, bubbleBackAction(bubbleMode = true, backClosesBubble = false))
        assertEquals(BubbleBack.FINISH, bubbleBackAction(bubbleMode = true, backClosesBubble = true))
        // Outside a bubble the setting is irrelevant: Back never finishes the app.
        assertEquals(BubbleBack.SHOW_LIST, bubbleBackAction(bubbleMode = false, backClosesBubble = false))
        assertEquals(BubbleBack.SHOW_LIST, bubbleBackAction(bubbleMode = false, backClosesBubble = true))
    }

    @Test
    fun backIsLeftToThePlatformSoItCanCollapseTheBubble() {
        assertFalse(
            "the app swallows Back, so SystemUI never gets to collapse the bubble",
            bubbleBackHandled(bubbleMode = true, backClosesBubble = false, sdk = 31)
        )
        assertFalse(
            "a newer platform release still collapses the bubble itself",
            bubbleBackHandled(bubbleMode = true, backClosesBubble = false, sdk = 36)
        )
        assertTrue(
            "closing the bubble is still the app's job when the setting is on",
            bubbleBackHandled(bubbleMode = true, backClosesBubble = true, sdk = 31)
        )
        assertTrue(
            "before Android 12 an unhandled Back finishes the chat and takes the bubble down",
            bubbleBackHandled(bubbleMode = true, backClosesBubble = false, sdk = 30)
        )
        assertTrue(
            "outside a bubble Back is always the app's",
            bubbleBackHandled(bubbleMode = false, backClosesBubble = false, sdk = 31)
        )
    }

    @Test
    fun theChatLetsThePlatformCollapseTheBubble() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "the chat does not receive the decision that lets the platform collapse the bubble",
            activity.contains("backHandled = bubbleBackHandled")
        )
        assertTrue(
            "the app still answers Back on every route while the bubble owns it",
            activity.contains("bubbleBackHandled && navRoute != \"chat\"")
        )
        assertTrue(
            "the chat always registers its leave-chat Back handler",
            src("java/com/anindra/messages/ui/ChatScreen.kt")
                .contains("BackHandler(enabled = backHandled, onBack = ::leaveChat)")
        )
        // The list is composed behind the chat even while the chat shows, so
        // its own handler was the one that swallowed the bubble's Back once the
        // chat declined it.
        for (file in listOf(
            "java/com/anindra/messages/ui/ConversationsScreen.kt",
            "java/com/anindra/messages/ui/legacy/LegacyConversationsScreen.kt"
        )) {
            val screen = src(file)
            assertTrue(
                "$file swallows Back while it sits behind the chat",
                screen.contains(
                    "BackHandler(enabled = backHandled && !selectionActive && !searching && !showArchived)"
                )
            )
            assertFalse(
                "$file still has an ungated default Back handler",
                screen.contains(
                    "BackHandler(enabled = !selectionActive && !searching && !showArchived)"
                )
            )
        }
    }

    @Test
    fun theBackArrowOpensTheListInAPlatformOwnedBubble() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "a bubble that leaves Back to the platform still has no route to the list",
            activity.contains("if (bubbleMode && !vm.settings.bubbleBackCloses) {")
        )
        assertTrue(
            "the chat is not told where the arrow goes",
            activity.contains("onOpenConversationList = bubbleArrowOpensList")
        )
        val chat = src("java/com/anindra/messages/ui/ChatScreen.kt")
        assertTrue(
            "the toolbar arrow still goes through the Back decision",
            chat.contains("onBack = ::openConversationList")
        )
        assertTrue(
            "the arrow has no route to the list",
            chat.contains("if (toList != null) toList() else onBack()")
        )
        assertTrue(
            "Back on the list inside a bubble goes somewhere instead of doing nothing",
            activity.contains("\"list\" -> navRoute = \"chat\"")
        )
    }

    @Test
    fun tappingAChatInTheBubbleListKeepsTheBubble() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "opening a chat from the list inside a bubble retires the bubble that hosts it",
            activity.contains(
                "val retireFullChatBubble: (Long) -> Unit = { id ->\n" +
                    "                    if (!bubbleMode) com.anindra.messages.sms.NotificationHelper"
            )
        )
        assertEquals(
            "both conversation lists (new and legacy) must open through the guarded helper",
            2,
            Regex("retireFullChatBubble\\(id\\)").findAll(activity).count()
        )
    }

    @Test
    fun stayingOnTheChatDoesNotNavigateToTheList() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "the bubble's Back has no path that stays on the chat",
            activity.contains("BubbleBack.STAY -> Unit")
        )
        assertTrue(
            "leaving for the list is no longer the non-bubble path",
            activity.contains("BubbleBack.SHOW_LIST -> navRoute = \"list\"")
        )
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
    fun theAppReceivesBackThroughTheDispatcher() {
        val manifest = src("AndroidManifest.xml")
        val application = Regex("""<application[\s\S]*?>""").find(manifest)?.value
            ?: error("no <application> element")
        assertTrue(
            "an opted-out app has no OnBackInvokedCallback, which is why a bubble on " +
                "Android 16 received no Back at all (#441)",
            application.contains("android:enableOnBackInvokedCallback=\"true\"")
        )
        assertFalse(
            "the legacy back path is back",
            application.contains("enableOnBackInvokedCallback=\"false\"")
        )
    }

    @Test
    fun backWhileTheChatOpensObeysTheSameRule() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "Back during the opening phase is unhandled in a bubble, so it looks dead",
            activity.contains(
                "\"opening\" -> when (bubbleBackAction(bubbleMode, vm.settings.bubbleBackCloses))"
            )
        )
    }

    @Test
    fun finishingABubbleReleasesItsNotification() {
        val activity = src("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "a bubble finished before its conversation id resolved leaves its notification behind",
            activity.contains("bubbleConversationId ?: bubbleLaunchAddress?.let { address ->")
        )
        assertTrue(
            "the finished bubble does not cancel its notification",
            activity.contains("clearConversationNotification(this, bubbleId)")
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
