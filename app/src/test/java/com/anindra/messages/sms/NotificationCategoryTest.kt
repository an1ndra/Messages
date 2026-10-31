package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-category notification channels (#339). The category decision and the
 * channel id it maps to must be one thing: the poster, the settings screen and
 * Diagnostics all read these functions rather than re-deriving the mapping.
 */
class NotificationCategoryTest {

    @Test
    fun groupWinsOverEverythingElse() {
        // A group of strangers is still a group, so GROUP beats UNKNOWN.
        assertEquals(NotificationCategory.GROUP, NotificationCategory.resolve(true, false, false))
        assertEquals(NotificationCategory.GROUP, NotificationCategory.resolve(true, false, true))
        assertEquals(NotificationCategory.GROUP, NotificationCategory.resolve(true, true, true))
    }

    @Test
    fun directIsOnlyAKnownReplyableOneToOne() {
        assertEquals(NotificationCategory.DIRECT, NotificationCategory.resolve(false, true, true))
    }

    @Test
    fun unknownCoversStrangersAndAlphanumericSenders() {
        // Not in Contacts.
        assertEquals(NotificationCategory.UNKNOWN, NotificationCategory.resolve(false, false, true))
        // Alphanumeric/short code: no reply is possible.
        assertEquals(NotificationCategory.UNKNOWN, NotificationCategory.resolve(false, true, false))
        assertEquals(NotificationCategory.UNKNOWN, NotificationCategory.resolve(false, false, false))
    }

    @Test
    fun categoryKeysAreStableLowercase() {
        assertEquals("direct", NotificationCategory.keyOf(NotificationCategory.DIRECT))
        assertEquals("group", NotificationCategory.keyOf(NotificationCategory.GROUP))
        assertEquals("unknown", NotificationCategory.keyOf(NotificationCategory.UNKNOWN))
    }

    @Test
    fun eachCategoryHasItsOwnChannelIdPerSound() {
        // The id carries both the category and the sound selection, because a
        // channel's sound cannot be changed after creation.
        assertEquals(
            "messages_direct_default",
            NotificationChannels.forCategory(NotificationCategory.DIRECT, "default", true)
        )
        assertEquals(
            "messages_group_default",
            NotificationChannels.forCategory(NotificationCategory.GROUP, "default", true)
        )
        assertEquals(
            "messages_unknown_default",
            NotificationChannels.forCategory(NotificationCategory.UNKNOWN, "default", true)
        )
        assertEquals(
            "messages_direct_uf09",
            NotificationChannels.forCategory(NotificationCategory.DIRECT, "universfield_09", true)
        )
        // Receive sound off always lands on the silent variant.
        assertEquals(
            "messages_direct_silent",
            NotificationChannels.forCategory(NotificationCategory.DIRECT, "universfield_09", false)
        )
    }

    @Test
    fun aConversationChannelIdCarriesNoTone() {
        // The tone used to be part of the id, which meant changing it in the app
        // deleted the channel and with it everything the user set in Android.
        // The id is now stable for the chat's lifetime.
        assertEquals("messages_convo_42", NotificationChannels.forConversation(42))
        assertEquals(
            "messages_convo_42",
            NotificationChannels.forConversation(42)
        )
    }

    @Test
    fun onlyAnOptedInChatPostsOnItsOwnChannel() {
        assertEquals(
            "messages_convo_7",
            NotificationChannels.forMessage(NotificationCategory.GROUP, "default", true, 7, true)
        )
        // No channel of its own: the chat posts on its category channel.
        assertEquals(
            "messages_group_default",
            NotificationChannels.forMessage(NotificationCategory.GROUP, "default", true, 7, false)
        )
        assertEquals(
            "messages_group_default",
            NotificationChannels.forMessage(NotificationCategory.GROUP, "default", true, null, false)
        )
    }

    @Test
    fun conversationChannelIdsRoundTripAndListTheirVariants() {
        assertEquals(42L, NotificationChannels.conversationIdOf("messages_convo_42"))
        // The old per-tone ids are still recognised, so they can be pruned.
        assertEquals(42L, NotificationChannels.conversationIdOf("messages_convo_42_default"))
        assertEquals(42L, NotificationChannels.conversationIdOf("messages_convo_42_silent"))
        assertEquals(null, NotificationChannels.conversationIdOf("messages_group_default"))
        assertEquals(null, NotificationChannels.conversationIdOf("messages"))
        // The live id plus the six ids the old scheme could have left behind.
        assertEquals(7, NotificationChannels.conversationVariants(42).size)
        assertTrue(
            NotificationChannels.forConversation(42) in NotificationChannels.conversationVariants(42)
        )
        assertTrue(
            "messages_convo_42_default" in
                NotificationChannels.legacyConversationVariants(42)
        )
    }

    @Test
    fun everyCategoryVariantCarriesItsCategorySegment() {
        NotificationCategory.entries.forEach { category ->
            val variants = NotificationChannels.categoryVariants(category)
            assertEquals(6, variants.size)
            assertEquals(6, variants.toSet().size)
            assertTrue(
                "$category variants must be distinct from the legacy ids",
                variants.none { it in NotificationChannels.LEGACY_VARIANTS }
            )
        }
    }

    @Test
    fun legacyIdsNeverCollideWithNewOnes() {
        // A stale legacy channel must never shadow a category or conversation
        // channel, or a delete-then-recreate would delete the live one.
        val live = NotificationCategory.entries
            .flatMap { NotificationChannels.categoryVariants(it) }
            .toSet() + NotificationChannels.forConversation(1)
        NotificationChannels.LEGACY_VARIANTS.forEach {
            assertTrue("legacy $it collides with a live channel id", it !in live)
            assertTrue("legacy $it is not a messages_* id", it.startsWith("messages"))
        }
    }

    @Test
    fun everyCategoryResolvesToADistinctTitle() {
        val titles = NotificationChannels.let {
            NotificationCategory.entries.map { c -> NotificationChannels.titleRes(c) }
        }
        assertEquals(titles.size, titles.toSet().size)
    }
}
