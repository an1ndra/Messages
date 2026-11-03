package com.anindra.messages.ui

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #394 follow-up: the reporter said a badge on the bubble "is better than a
 * plaing icon on it", pointing at Google Messages. A starred bubble used to
 * carry a bare 14.dp star glyph in its corner; it now draws a small framed
 * circle so the marker reads as a badge rather than as part of the message.
 *
 * The badge is pixels, so the shape is pinned on the source that draws it. The
 * runtime half — that the badge node is actually there and the size of a badge,
 * not a glyph — lives in `scripts/test-starred-messages.sh`, which asserts on
 * the `uiautomator` bounds of the "Starred message" node.
 */
class StarBadgeTest {

    private val chat = File(
        LocaleCatalog.repoRoot(),
        "app/src/main/java/com/anindra/messages/ui/ChatScreen.kt"
    ).readText()

    /** The text of the `StarBadge` composable, from its declaration to the
     * column-zero closing brace. */
    private fun starBadge(): String {
        val decl = Regex("(?:internal|private|public)\\s+fun StarBadge\\(").find(chat)
            ?: error("StarBadge composable is missing from ChatScreen.kt")
        return chat.substring(decl.range.first).substringBefore("\n}\n")
    }

    @Test
    fun theBubbleDrawsTheBadgeComposable() {
        assertTrue(
            "the starred branch no longer draws a badge",
            Regex("if \\(msg\\.starred\\)\\s*\\{\\s*StarBadge\\(").containsMatchIn(chat)
        )
    }

    @Test
    fun theBadgeIsAFramedCircleRatherThanABareGlyph() {
        val body = starBadge()
        assertTrue("the badge is not a Surface", body.contains("Surface("))
        assertTrue("the badge is not circular", body.contains("shape = CircleShape"))
        assertTrue("the badge has no surface fill", body.contains("color = cs.surface"))
        assertTrue(
            "the badge has no outline, so it reads as a floating glyph",
            body.contains("border = BorderStroke(1.dp, cs.outlineVariant)")
        )
        assertTrue(
            "the badge has no elevation, so it does not sit on the bubble",
            body.contains("shadowElevation")
        )
    }

    @Test
    fun theBadgeCarriesTheAccessibilityDescriptionItself() {
        val body = starBadge()
        assertTrue(
            "the badge's node has no content description of its own; the inner icon's " +
                "smaller bounds would be reported instead",
            body.contains("semantics { contentDescription = desc }")
        )
        assertTrue(body.contains("chat_message_starred"))
    }

    @Test
    fun theStarredBranchNoLongerPaintsABareIcon() {
        val start = Regex("if \\(msg\\.starred\\)").find(chat)?.range?.first
            ?: error("the starred branch is gone")
        val window = chat.substring(start, minOf(chat.length, start + 400))
        assertFalse(
            "the starred bubble still paints a bare Icon; it should delegate to StarBadge",
            window.contains("Icon(")
        )
    }
}
