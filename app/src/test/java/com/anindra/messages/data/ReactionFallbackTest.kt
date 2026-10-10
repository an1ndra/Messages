package com.anindra.messages.data

import com.anindra.messages.ui.MessageReactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}