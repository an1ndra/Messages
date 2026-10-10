package com.anindra.messages.mms.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SmsManagerMmsPlatform` needs a real `Context`, which a plain JVM test cannot
 * build, so the rule that decides which manager to ask for is pinned on its own.
 *
 * The rule matters because the app stores "whichever SIM holds the default SMS
 * role" as a negative id and hands that straight to `Mms.send`: passing it to
 * `getSmsManagerForSubscriptionId` asks the MMS service for a subscription that
 * does not exist, which fails the send rather than using the default SIM.
 */
class DefaultSmsManagerTest {
    @Test
    fun aNonPositiveIdMeansTheDefaultSmsSim() {
        assertTrue(usesDefaultSmsManager(-1))
        assertTrue(usesDefaultSmsManager(0))
    }

    @Test
    fun aNamedLineIsNotTheDefaultSim() {
        assertFalse(usesDefaultSmsManager(1))
        assertFalse(usesDefaultSmsManager(99))
    }
}