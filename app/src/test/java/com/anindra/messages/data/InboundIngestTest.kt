package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundIngestTest {

    @Test
    fun aNormalMessageRestoresATrashedThread() {
        assertTrue(InboundIngest.restoresTrashedConversation(InboundKind.NORMAL))
    }

    @Test
    fun aKeywordBlockedMessageLeavesTheThreadTrashed() {
        assertFalse(InboundIngest.restoresTrashedConversation(InboundKind.BLOCKED_KEYWORD))
    }

    @Test
    fun aBlockedNumberLeavesTheThreadTrashed() {
        assertFalse(InboundIngest.restoresTrashedConversation(InboundKind.BLOCKED_NUMBER))
    }
}
