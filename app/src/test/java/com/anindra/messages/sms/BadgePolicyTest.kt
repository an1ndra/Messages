package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgePolicyTest {
    @Test
    fun publishesTheUnreadTotal() {
        assertEquals(7, BadgePolicy.badgeCount(7))
    }

    @Test
    fun neverPublishesANegativeCount() {
        assertEquals(0, BadgePolicy.badgeCount(-3))
    }

    @Test
    fun onlyPublishesANumberWhenSomethingIsUnread() {
        assertTrue(BadgePolicy.publishesNumber(1))
        assertTrue(BadgePolicy.publishesNumber(42))
        assertFalse(BadgePolicy.publishesNumber(0))
    }

    @Test
    fun dismissesTheMessageAndFailureNotificationOfOneConversation() {
        // Same id twice: untagged for the message, and under the "failed" tag
        // that showSendFailed posts with.
        assertEquals(31 to 31, BadgePolicy.idsToDismiss(31L, null))
    }

    @Test
    fun fallsBackToTheAddressHashWhenThereIsNoConversationRow() {
        assertEquals(1234 to 1234, BadgePolicy.idsToDismiss(null, 1234))
    }

    @Test
    fun dismissesNothingWithoutAnyIdentifier() {
        assertNull(BadgePolicy.idsToDismiss(null, null))
    }
}
