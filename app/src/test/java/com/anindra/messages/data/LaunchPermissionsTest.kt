package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPermissionsTest {

    private val allGranted: (String) -> Boolean = { true }
    private val noneGranted: (String) -> Boolean = { false }

    @Test
    fun theRoleHandsOverTheSmsSetSoAHoldingLaunchAsksForNothing() {
        val needed = LaunchPermissions.stillNeeded(
            roleHeld = true, sdkInt = 33, granted = noneGranted
        )
        assertEquals(listOf(LaunchPermissions.NOTIFICATIONS), needed)
    }

    @Test
    fun aDeclinedRoleStillAsksForTheSmsSetItCannotWorkWithout() {
        val needed = LaunchPermissions.stillNeeded(
            roleHeld = false, sdkInt = 33, granted = noneGranted
        )
        assertEquals(
            LaunchPermissions.SMS_SET + LaunchPermissions.NOTIFICATIONS,
            needed
        )
    }

    @Test
    fun notificationsAreRuntimeOnlyFromApi33() {
        assertTrue(
            LaunchPermissions.stillNeeded(true, 32, noneGranted).isEmpty()
        )
        assertEquals(
            listOf(LaunchPermissions.NOTIFICATIONS),
            LaunchPermissions.stillNeeded(true, 33, noneGranted)
        )
    }

    @Test
    fun alreadyGrantedPermissionsAreNeverAskedForAgain() {
        val granted: (String) -> Boolean = { it == LaunchPermissions.SMS_SET.first() }
        val needed = LaunchPermissions.stillNeeded(false, 29, granted)
        assertEquals(LaunchPermissions.SMS_SET.drop(1), needed)
    }

    @Test
    fun contactsAreNeverPartOfALaunchAsk() {
        val needed = LaunchPermissions.stillNeeded(false, 33, noneGranted)
        assertFalse(LaunchPermissions.CONTACTS in needed)
        assertTrue(LaunchPermissions.CONTACTS !in LaunchPermissions.SMS_SET)
    }
}
