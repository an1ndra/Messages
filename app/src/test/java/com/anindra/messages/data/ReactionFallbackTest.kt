package com.anindra.messages.data

import com.anindra.messages.ui.MessageReactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reaction notice one device sends as text (#188). Nothing parses it any
 * more — it is an ordinary message on the receiving side — so these tests only
 * pin the wording and that a notice survives one SMS segment.
 */
class ReactionFallbackTest {

    @Test
    fun addReadsAsASentence() {
        assertEquals("Reacted 👍 to hello world", ReactionFallback.add("👍", "hello world"))
    }

    @Test
    fun removeReadsAsASentence() {
        assertEquals("Removed ❤️ from hi", ReactionFallback.remove("❤️", "hi"))
    }

    @Test
    fun everyOfferedEmojiFitsTheFormat() {
        for (emoji in MessageReactions.EMOJI) {
            val body = ReactionFallback.add(emoji, "hello world")
            assertTrue("no delimiter after the emoji in '$body'", body.startsWith("Reacted $emoji to "))
            assertEquals(emoji.length + "Reacted  to hello world".length, body.length)
        }
    }

    @Test
    fun aNoticeStaysOneSegmentAndUnquoted() {
        val snippet = MessageReactions.quote("  hello\n\tworld   again  ")
        assertEquals("hello world again", snippet)
        val body = ReactionFallback.add("😂", snippet)
        assertFalse("a quote would need escaping on the wire", body.contains('"'))
        assertEquals(1, body.lines().size)
        assertFalse("a newline would split the notice", body.contains('\n'))
    }

    @Test
    fun theSnippetIsCappedSoTheNoticeCannotOverflowASms() {
        assertTrue(MessageReactions.quote("x".repeat(500)).length <= 60)
    }

    @Test
    fun iosQuotedEmojiReactionFindsTheTarget() {
        assertEquals(
            ReactionFallback.Parsed("😂", "ای هعی 😂", true),
            ReactionFallback.parse("Reacted 😂 to “ای هعی 😂”")
        )
        assertEquals(
            ReactionFallback.Parsed("😂", "آیفون دار مرفه 📱", true),
            ReactionFallback.parse("Reacted 😂 to “آیفون دار مرفه 📱”")
        )
        assertEquals(
            ReactionFallback.Parsed("😂", "plain quotes", true),
            ReactionFallback.parse("Reacted 😂 to \"plain quotes\"")
        )
    }

    @Test
    fun iosWordedTapbacksMapToEmoji() {
        assertEquals(
            ReactionFallback.Parsed("❓", "لفت دونوم چنه", true),
            ReactionFallback.parse("Questioned “لفت دونوم چنه”")
        )
        assertEquals(
            ReactionFallback.Parsed("👍", "hello", true),
            ReactionFallback.parse("Liked “hello”")
        )
        assertEquals(
            ReactionFallback.Parsed("❤️", "hello", true),
            ReactionFallback.parse("Loved “hello”")
        )
        assertEquals(
            ReactionFallback.Parsed("👎", "hello", true),
            ReactionFallback.parse("Disliked “hello”")
        )
        assertEquals(
            ReactionFallback.Parsed("😂", "hello", true),
            ReactionFallback.parse("Laughed at “hello”")
        )
        assertEquals(
            ReactionFallback.Parsed("❗", "hello", true),
            ReactionFallback.parse("Emphasized “hello”")
        )
    }

    @Test
    fun iosWordedRemovalsMapToEmoji() {
        val expected = mapOf(
            "a heart" to "❤️",
            "a like" to "👍",
            "a dislike" to "👎",
            "a laugh" to "😂",
            "an exclamation" to "❗",
            "a question mark" to "❓"
        )
        for ((words, emoji) in expected) {
            assertEquals(
                "Removed $words",
                ReactionFallback.Parsed(emoji, "hello", false),
                ReactionFallback.parse("Removed $words from “hello”")
            )
        }
    }

    @Test
    fun iosQuotesMayCarrySpacesInsideThem() {
        assertEquals(
            ReactionFallback.Parsed("👍", "hello", false),
            ReactionFallback.parse("Removed a like from “ hello ”")
        )
    }

    @Test
    fun ourOwnQuoteFreeNoticeStaysAnOrdinaryMessage() {
        assertNull(ReactionFallback.parse("Reacted 👍 to hello world"))
        assertNull(ReactionFallback.parse("Removed 👍 from hello world"))
    }

    @Test
    fun ordinarySentencesAreNotMistakenForTapbacks() {
        assertNull(ReactionFallback.parse("Removed you from the group"))
        assertNull(ReactionFallback.parse("Questioned hello without quotes"))
        assertNull(ReactionFallback.parse("I Loved “Dune”"))
        // Real curly quotes only for the named tapbacks; a typed sentence uses plain ones.
        assertNull(ReactionFallback.parse("Loved \"Dune\""))
        assertNull(ReactionFallback.parse("Loved the film"))
    }
}
