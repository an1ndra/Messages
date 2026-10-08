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
                receiveSoundEnabled = true,
                privacyMode = false
            )
        )
    }

    @Test
    fun neverWakesOnAnUnlockedScreen() {
        assertFalse(
            NotificationHelper.shouldWake(
                keyguardLocked = false,
                notificationsEnabled = true,
                receiveSoundEnabled = true,
                privacyMode = false
            )
        )
    }

    @Test
    fun neverWakesWithNotificationsOff() {
        assertFalse(
            NotificationHelper.shouldWake(
                keyguardLocked = true,
                notificationsEnabled = false,
                receiveSoundEnabled = true,
                privacyMode = false
            )
        )
    }

    @Test
    fun neverWakesWithReceiveSoundOff() {
        assertFalse(
            NotificationHelper.shouldWake(
                keyguardLocked = true,
                notificationsEnabled = true,
                receiveSoundEnabled = false,
                privacyMode = false
            )
        )
    }

    @Test
    fun neverWakesInPrivacyMode() {
        assertFalse(
            NotificationHelper.shouldWake(
                keyguardLocked = true,
                notificationsEnabled = true,
                receiveSoundEnabled = true,
                privacyMode = true
            )
        )
    }
}