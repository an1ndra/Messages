package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Local reaction toggle (issue #188): one reaction per emoji, tap to add or
 * remove, and any counts carried in from an import must survive edits to other
 * emoji.
 */
class MessageReactionsTest {

    @Test
    fun tappingAnEmojiAddsItAtCountOne() {
        assertEquals(mapOf("👍" to 1), MessageReactions.toggle(emptyMap(), "👍"))
    }

    @Test
    fun tappingAnExistingEmojiRemovesIt() {
        assertEquals(emptyMap<String, Int>(), MessageReactions.toggle(mapOf("👍" to 1), "👍"))
    }

    @Test
    fun otherReactionsSurvive() {
        assertEquals(
            mapOf("❤️" to 1, "🔥" to 1),
            MessageReactions.toggle(mapOf("❤️" to 1), "🔥")
        )
    }

    @Test
    fun importedCountsOnOtherEmojiArePreserved() {
        assertEquals(
            mapOf("👍" to 2, "🔥" to 1),
            MessageReactions.toggle(mapOf("👍" to 2), "🔥")
        )
    }

    @Test
    fun displayOrderFollowsTheBarAndThenExtras() {
        val order = MessageReactions.ordered(mapOf("🎉" to 1, "👍" to 2, "🦄" to 1))
        assertEquals(listOf("👍" to 2, "🎉" to 1, "🦄" to 1), order)
    }

    @Test
    fun quoteCollapsesWhitespaceAndTruncates() {
        assertEquals("a b", MessageReactions.quote("  a\n b  "))
        assertTrue(MessageReactions.quote("x".repeat(200)).length <= 60)
    }

    @Test
    fun theBarIsHiddenWhileALockedMessageIsConcealed() {
        // Reacting to a concealed locked message would put its body in the SMS
        // fallback, defeating the lock — so the bar must stay closed until it is
        // unlocked.
        val src = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/anindra/messages/ui/ChatScreen.kt") }
            .firstOrNull { it.isFile }
            ?.readText() ?: error("ChatScreen.kt not found")
        assertTrue(
            "ChatScreen must gate the reaction bar on !isLockedAndHidden",
            src.contains("showReactionBar && !isLockedAndHidden")
        )
    }

    @Test
    fun theBarIsHiddenWhereAMessageCannotBeSent() {
        // A reaction sends an SMS fallback, so it must be offered on the same
        // terms as the composer: a dialable, unblocked address. Otherwise an
        // alphanumeric sender ID (which cannot receive replies) got a reaction
        // whose fallback could never go out.
        val src = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/anindra/messages/ui/ChatScreen.kt") }
            .firstOrNull { it.isFile }
            ?.readText() ?: error("ChatScreen.kt not found")
        assertTrue(
            "the reaction bar must be gated on canReact",
            src.contains("showReactionBar = reactingMessageId == msg.id") && src.contains("canReact")
        )
        assertTrue(
            "canReact must require a dialable, unblocked address",
            src.contains("isPhoneNumber(addr) && !numberIsBlocked")
        )
        assertTrue(
            "the picker is for a single message only",
            src.contains("selectedMessageIds.size == 1")
        )
    }
}
