package com.anindra.messages.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {

    @Test
    fun suppressOnlyWhenForegroundAndThreadOpen() {
        assertTrue(NotificationPolicy.skipForOpenThread(appInForeground = true, threadOpen = true))
        assertFalse(NotificationPolicy.skipForOpenThread(appInForeground = true, threadOpen = false))
        assertFalse(NotificationPolicy.skipForOpenThread(appInForeground = false, threadOpen = true))
        assertFalse(NotificationPolicy.skipForOpenThread(appInForeground = false, threadOpen = false))
    }

    @Test
    fun openThreadDoesNotCountAsUnread() {
        assertFalse(NotificationPolicy.countsAsUnread(appInForeground = true, threadOpen = true))
    }

    @Test
    fun everyOtherArrivalCountsAsUnread() {
        assertTrue(NotificationPolicy.countsAsUnread(appInForeground = true, threadOpen = false))
        assertTrue(NotificationPolicy.countsAsUnread(appInForeground = false, threadOpen = true))
        assertTrue(NotificationPolicy.countsAsUnread(appInForeground = false, threadOpen = false))
    }
}
