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
}