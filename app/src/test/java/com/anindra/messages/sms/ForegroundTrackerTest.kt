package com.anindra.messages.sms

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Open-conversation tracking for notification suppression (#354). A bubble and
 * the full-screen app can show two chats at once, so the tracker counts each
 * window's registration instead of holding a single address: closing the bubble
 * used to clear the address of the chat still visible in the app window, which
 * made the next message in that chat post a notification for a thread the user
 * was reading.
 */
class ForegroundTrackerTest {

    @Before
    fun setUp() = ForegroundTracker.reset()

    @After
    fun tearDown() = ForegroundTracker.reset()

    @Test
    fun aChatIsOpenWhileItIsOnScreen() {
        ForegroundTracker.openConversation("+15550001")
        assertTrue(ForegroundTracker.isConversationOpen("+15550001"))

        ForegroundTracker.closeConversation("+15550001")
        assertFalse(ForegroundTracker.isConversationOpen("+15550001"))
    }

    @Test
    fun closingOneWindowLeavesTheSameChatOpenInAnother() {
        ForegroundTracker.openConversation("+15550001")
        ForegroundTracker.openConversation("+15550001")

        ForegroundTracker.closeConversation("+15550001")
        assertTrue(
            "the app window still shows this chat, so its notification stays suppressed",
            ForegroundTracker.isConversationOpen("+15550001")
        )

        ForegroundTracker.closeConversation("+15550001")
        assertFalse(ForegroundTracker.isConversationOpen("+15550001"))
    }

    @Test
    fun chatsAreTrackedIndependently() {
        ForegroundTracker.openConversation("+15550001")
        ForegroundTracker.openConversation("+15550002")

        ForegroundTracker.closeConversation("+15550001")
        assertFalse(ForegroundTracker.isConversationOpen("+15550001"))
        assertTrue(ForegroundTracker.isConversationOpen("+15550002"))
    }

    @Test
    fun closingAChatThatNeverOpenedDoesNotSuppressItsNotifications() {
        ForegroundTracker.closeConversation("+15550009")
        assertFalse(ForegroundTracker.isConversationOpen("+15550009"))
    }

    @Test
    fun aNullAddressIsNeverOpen() {
        ForegroundTracker.openConversation(null)
        assertFalse(ForegroundTracker.isConversationOpen(null))
        assertFalse(ForegroundTracker.isConversationOpen("+15550001"))
    }

    @Test
    fun foregroundRoundTrips() {
        assertFalse(ForegroundTracker.isAppInForeground)
        ForegroundTracker.setAppForeground(true)
        assertTrue(ForegroundTracker.isAppInForeground)
        ForegroundTracker.setAppForeground(false)
        assertFalse(ForegroundTracker.isAppInForeground)
    }
}
