package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashReasonTest {

    @Test
    fun keysAreStable() {
        assertEquals("manual", TrashReason.MANUAL)
        assertEquals("blocked_keyword", TrashReason.BLOCKED_KEYWORD)
    }

    @Test
    fun onlyTheBlockedReasonIsFlagged() {
        assertTrue(TrashReason.isBlockedKeyword(TrashReason.BLOCKED_KEYWORD))
        assertFalse(TrashReason.isBlockedKeyword(TrashReason.MANUAL))
        assertFalse(TrashReason.isBlockedKeyword(""))
    }

    @Test
    fun conversationsDefaultToManual() {
        val convo = Conversation(
            id = 1L, address = "+15551230000", name = "n", snippet = "", timestamp = 0L,
            unreadCount = 0, isMe = false
        )
        assertEquals(TrashReason.MANUAL, convo.deletedReason)
    }
}
