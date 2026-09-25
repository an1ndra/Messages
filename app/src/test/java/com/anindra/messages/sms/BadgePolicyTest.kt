package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgePolicyTest {
    @Test
    fun oneNotificationContributesExactlyOneToTheBadge() {
        // Launchers sum these across active notifications, so every value must
        // be 1 - a per-conversation total renders as nonsense once there is more
        // than one active notification.
        assertEquals(1, BadgePolicy.PER_NOTIFICATION)
        assertEquals(1, BadgePolicy.badgeCount(BadgePolicy.PER_NOTIFICATION))
    }

    @Test
    fun neverPublishesANonPositiveCount() {
        // 0 is read by launchers as "no badge", so it is never published.
        assertEquals(1, BadgePolicy.badgeCount(0))
        assertEquals(1, BadgePolicy.badgeCount(-3))
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
