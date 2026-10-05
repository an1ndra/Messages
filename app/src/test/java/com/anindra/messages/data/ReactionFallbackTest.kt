package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The app-to-app reaction protocol (#188): what one device sends as text and the
 * other parses back into a reaction.
 */
class ReactionFallbackTest {

    @Test
    fun addRoundTrips() {
        val body = ReactionFallback.add("👍", "hello world")
        assertEquals("Reacted 👍 to hello world", body)
        assertEquals(
            ReactionFallback.Parsed("👍", "hello world", added = true),
            ReactionFallback.parse(body)
        )
    }

    @Test
    fun removeRoundTrips() {
        val body = ReactionFallback.remove("❤️", "hi")
        assertEquals("Removed ❤️ from hi", body)
        assertEquals(
            ReactionFallback.Parsed("❤️", "hi", added = false),
            ReactionFallback.parse(body)
        )
    }

    @Test
    fun ordinaryTextIsNotAReaction() {
        assertNull(ReactionFallback.parse("hello world"))
        assertNull(ReactionFallback.parse("Reacted to your message"))
        assertNull(ReactionFallback.parse("I reacted 👍 to that"))
        // A real sentence: the "emoji" slot is an ASCII word, so it is not ours.
        assertNull(ReactionFallback.parse("Removed you from the group"))
    }

    @Test
    fun aMultiWordSnippetSurvives() {
        val parsed = ReactionFallback.parse(ReactionFallback.add("😂", "say hi now"))
        assertEquals("😂", parsed!!.emoji)
        assertEquals("say hi now", parsed.snippet)
    }
}
