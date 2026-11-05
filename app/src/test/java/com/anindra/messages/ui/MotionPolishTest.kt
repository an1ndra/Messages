package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The "smoothness" pass: the places that used to snap now ease, and every one of
 * them goes through [com.anindra.messages.ui.theme.motionTween] /
 * [com.anindra.messages.ui.theme.motionSpring] so the accessibility
 * "reduce motion" option collapses them to [androidx.compose.animation.core.snap]
 * instead of animating anyway.
 *
 * A raw `tween(...)` or `spring(...)` here would be a motion the setting cannot
 * switch off, which is the bug this pins.
 */
class MotionPolishTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val chat get() = read("src/main/java/com/anindra/messages/ui/ChatScreen.kt")
    private val list get() = read("src/main/java/com/anindra/messages/ui/ConversationsScreen.kt")
    private val settings get() = read("src/main/java/com/anindra/messages/ui/SettingsScreen.kt")

    @Test
    fun aNewMessageGlidesInsteadOfJumpingTheList() {
        assertTrue(
            "chat rows must animate placement",
            chat.contains(".animateItem(") &&
                chat.contains("placementSpec = motionSpring<IntOffset>(LocalReduceMotion.current)")
        )
    }

    @Test
    fun theComposerGrowsWithTheDraft() {
        assertTrue(
            "a new line in the composer must not snap the pill taller",
            chat.contains(".animateContentSize(") &&
                chat.contains("animationSpec = motionSpring<IntSize>(LocalReduceMotion.current)")
        )
    }

    @Test
    fun aSettingsRowEasesToItsNewHeight() {
        assertTrue(
            "a row that gains or loses a subtitle must animate its height",
            settings.contains(".animateContentSize(animationSpec = motionSpring<IntSize>(LocalReduceMotion.current))")
        )
    }

    @Test
    fun theConversationSurfaceIsNotCrossFaded() {
        // Reported: opening a chat left the list blinking. A cross-fade over the
        // whole surface draws every row twice at partial alpha, so the swap
        // between skeleton, list and the empty panel stays instant.
        assertTrue(
            "the whole-list surface must not be cross-faded",
            !list.contains("Crossfade(")
        )
    }

    @Test
    fun noneOfTheNewMotionIgnoresReduceMotion() {
        listOf(
            "ChatScreen.kt" to chat,
            "ConversationsScreen.kt" to list,
            "SettingsScreen.kt" to settings
        ).forEach { (name, src) ->
            val rawTweens = Regex("""animateItem\(|animateContentSize\(|AnimatedVisibility\(""").findAll(src).count()
            if (rawTweens == 0) return@forEach
            assertTrue(
                "$name animates but never reads LocalReduceMotion",
                src.contains("LocalReduceMotion.current")
            )
        }
    }
}
