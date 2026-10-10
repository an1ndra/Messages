package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationLivenessTest {

    @Test
    fun anEmptyChatWithNothingQueuedIsTrashed() {
        assertFalse(ConversationLiveness.keepAlive(messageCount = 0, pendingScheduled = 0, draft = ""))
    }

    @Test
    fun aScheduledMessageKeepsTheConversationAlive() {
        // Regression: scheduling clears the draft, and a chat whose only content
        // was that draft has no `messages` rows, so it used to be trashed on the
        // way out and disappear from the conversation list.
        assertTrue(ConversationLiveness.keepAlive(messageCount = 0, pendingScheduled = 1, draft = ""))
    }

    @Test
    fun aDraftKeepsTheConversationAlive() {
        assertTrue(ConversationLiveness.keepAlive(messageCount = 0, pendingScheduled = 0, draft = "hi"))
    }

    @Test
    fun anExistingMessageKeepsTheConversationAlive() {
        assertTrue(ConversationLiveness.keepAlive(messageCount = 3, pendingScheduled = 0, draft = ""))
    }

    @Test
    fun aBlankDraftDoesNotKeepItAlive() {
        assertFalse(ConversationLiveness.keepAlive(messageCount = 0, pendingScheduled = 0, draft = "   "))
    }
}