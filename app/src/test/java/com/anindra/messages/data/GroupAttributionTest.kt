package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Group attribution. An inbound SMS names a sender but not a conversation, so a
 * message is only filed into a group when exactly one group could be meant.
 */
class GroupAttributionTest {

    /**
     * Mirrors the real rule: a 1:1 with the sender always wins, and only
     * otherwise does an unambiguous group take it.
     */
    private fun route(hasOneToOne: Boolean, senderGroups: List<Set<String>>): String? {
        if (hasOneToOne) return "one-to-one"
        val groups = senderGroups.filter { it.size > 1 }.distinct()
        return groups.singleOrNull()?.let { "that group" }
    }

    @Test
    fun senderInExactlyOneGroupAndNoOneToOneIsAttributedToIt() {
        val result = route(hasOneToOne = false, senderGroups = listOf(setOf("me", "alex")))
        assertEquals("that group", result)
    }

    @Test
    fun senderInTwoGroupsIsNotAttributed() {
        // Guessing would put the message in a group the user did not mean, so
        // the reply stays in their own 1:1 thread instead.
        val result = route(
            hasOneToOne = false,
            senderGroups = listOf(setOf("me", "alex"), setOf("alex", "carlos"))
        )
        assertEquals(null, result)
    }

    @Test
    fun senderWithNoGroupIsNotAttributed() {
        assertEquals(null, route(hasOneToOne = false, senderGroups = listOf(setOf("alex"))))
    }

    @Test
    fun aOneToOneThreadWinsOverAGroup() {
        // Having a private thread with someone, a text from them almost certainly
        // means that thread rather than the group.
        val result = route(
            hasOneToOne = true,
            senderGroups = listOf(setOf("me", "alex"))
        )
        assertEquals("one-to-one", result)
    }

    @Test
    fun aOneToOneWinsEvenWithTwoGroups() {
        val result = route(
            hasOneToOne = true,
            senderGroups = listOf(setOf("me", "alex"), setOf("alex", "carlos"))
        )
        assertEquals("one-to-one", result)
    }

    @Test
    fun duplicateMembershipRowsDoNotCountAsTwoGroups() {
        // A conversation can only be a group once regardless of how many rows
        // name the same sender, so DISTINCT matters.
        val groups = listOf(setOf("me", "alex"), setOf("me", "alex"))
        assertEquals(1, groups.distinct().size)
        assertEquals("that group", route(hasOneToOne = false, senderGroups = groups))
    }

    @Test
    fun aSingleMemberConversationIsNotAGroup() {
        assertFalse(setOf("alex").size > 1)
        assertTrue(setOf("me", "alex").size > 1)
    }
}
