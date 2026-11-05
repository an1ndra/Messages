package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgePolicyTest {
    @Test
    fun aNotificationCarriesItsOwnUnreadCount() {
        // #345: launchers sum these across the app's live notifications, so the
        // icon badge is the unread *message* total - the number the inbox shows.
        // A flat 1 counted unread conversations instead.
        assertEquals(1, BadgePolicy.PER_NOTIFICATION)
        assertEquals(5, BadgePolicy.badgeCount(5))
        assertEquals(1, BadgePolicy.badgeCount(1))
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

    @Test
    fun blockingCancelsEveryConversationIdAndTheHashFallback() {
        // #345: the sender may own several conversation rows (both spellings of
        // the address), and a notification may predate its conversation row, so
        // the hash id the show() path fell back to is cancelled alongside.
        assertTrue(
            BadgePolicy.idsToDismissOnBlock(listOf(31L, 64L), "+15557770001")
                .containsAll(listOf(31, 64, "+15557770001".hashCode()))
        )
    }

    @Test
    fun blockingDiscardsDuplicateNotificationIds() {
        // Two conversation rows can round-trip to one notification id (Long ->
        // Int truncation), which would cancel the same live notification twice.
        assertEquals(
            listOf(1),
            BadgePolicy.idsToDismissOnBlock(listOf(1L, (1L shl 32) + 1L), null)
        )
    }

    @Test
    fun blockingWithNoConversationsStillCancelsTheHashId() {
        assertEquals(
            listOf("+15557770001".hashCode()),
            BadgePolicy.idsToDismissOnBlock(emptyList(), "+15557770001")
        )
    }

    @Test
    fun blockingCancelsNothingWithoutAnythingToCancel() {
        assertTrue(BadgePolicy.idsToDismissOnBlock(emptyList(), null).isEmpty())
    }
}
