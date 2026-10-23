package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision to bubble a conversation (#354) is pure, so the cases that made
 * it wrong in review are pinned here instead of on a device: a bubble needs a
 * conversation to key its shortcut on and a sender that can be replied to.
 */
class NotificationBubbleTest {

    @Test
    fun attachesOnlyWhenThereIsAConversationToKeyTheShortcutOn() {
        assertFalse(NotificationBubble.shouldAttach(conversationId = null, replyable = true))
        assertTrue(NotificationBubble.shouldAttach(conversationId = 7L, replyable = true))
    }

    @Test
    fun aSenderThatCannotBeRepliedToNeverGetsABubble() {
        assertFalse(NotificationBubble.shouldAttach(conversationId = 7L, replyable = false))
    }

    @Test
    fun shortcutIdsAreStableAndPerConversation() {
        assertEquals(NotificationBubble.shortcutId(7L), NotificationBubble.shortcutId(7L))
        assertTrue(NotificationBubble.shortcutId(7L) != NotificationBubble.shortcutId(8L))
    }
}
