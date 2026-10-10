package com.anindra.messages.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeOnLockTest {

    @Test
    fun wakesWhenLockedAndConfiguredToAlert() {
        assertTrue(
            NotificationHelper.shouldWake(
                keyguardLocked = true,
                notificationsEnabled = true,
                receiveSoundEnabled = true
            )
        )
    }

    @Test
    fun neverWakesOnAnUnlockedScreen() {
        assertFalse(
            NotificationHelper.shouldWake(
                keyguardLocked = false,
                notificationsEnabled = true,
                receiveSoundEnabled = true
            )
        )
    }

    @Test
    fun neverWakesWithNotificationsOff() {
        assertFalse(
            NotificationHelper.shouldWake(
                keyguardLocked = true,
                notificationsEnabled = false,
                receiveSoundEnabled = true
            )
        )
    }

    @Test
    fun neverWakesWithReceiveSoundOff() {
        assertFalse(
            NotificationHelper.shouldWake(
                keyguardLocked = true,
                notificationsEnabled = true,
                receiveSoundEnabled = false
            )
        )
    }

    @Test
    fun wakesInPrivacyMode() {
        assertTrue(
            NotificationHelper.shouldWake(
                keyguardLocked = true,
                notificationsEnabled = true,
                receiveSoundEnabled = true
            )
        )
    }
}
