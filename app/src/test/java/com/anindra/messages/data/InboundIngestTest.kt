package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundIngestTest {

    @Test
    fun aNormalMessageDoesNotReuseATrashedThread() {
        assertFalse(InboundIngest.mayReuseTrashedConversation(InboundKind.NORMAL))
    }

    @Test
    fun aKeywordBlockedMessageMayUseTheTrashedThread() {
        assertTrue(InboundIngest.mayReuseTrashedConversation(InboundKind.BLOCKED_KEYWORD))
    }

    @Test
    fun aBlockedNumberMayUseTheTrashedThread() {
        assertTrue(InboundIngest.mayReuseTrashedConversation(InboundKind.BLOCKED_NUMBER))
    }
}
