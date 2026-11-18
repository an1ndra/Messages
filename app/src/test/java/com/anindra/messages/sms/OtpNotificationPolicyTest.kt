package com.anindra.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #476: a message carrying a one-time code is posted as a sensitive
 * notification, with a public version whose digits are redacted.
 *
 * The detection is OtpDetector's — the same code the tap-to-copy action uses —
 * so this only has to pin the two decisions: which messages count, and what the
 * lock-screen version is allowed to say.
 */
class OtpNotificationPolicyTest {

    @Test
    fun aMessageWithACodeIsSensitive() {
        assertTrue(OtpNotificationPolicy.carriesCode("Your one-time code is 482913. Do not share it."))
        assertTrue(OtpNotificationPolicy.carriesCode("482913 is your verification code"))
        assertTrue(OtpNotificationPolicy.carriesCode("OTP: 4821"))
    }

    @Test
    fun ordinaryMessagesAreNot() {
        assertFalse(OtpNotificationPolicy.carriesCode("See you at seven"))
        assertFalse(OtpNotificationPolicy.carriesCode("On my way, will pick up milk"))
        assertFalse(OtpNotificationPolicy.carriesCode(""))
        assertFalse(OtpNotificationPolicy.carriesCode("   "))
    }

    @Test
    fun thePublicVersionKeepsTheWordingAndLosesTheDigits() {
        assertEquals(
            "Your one-time code is 000000. Do not share it.",
            OtpNotificationPolicy.redact("Your one-time code is 482913. Do not share it.")
        )
        assertEquals("See you at seven", OtpNotificationPolicy.redact("See you at seven"))
        assertEquals("", OtpNotificationPolicy.redact(""))
    }

    @Test
    fun redactionDoesNotChangeTheLength() {
        // The lock screen shows the same shape as the real message, so the
        // public version cannot be told apart from a code of a different length.
        val body = "Code 1234 expires in 10 minutes"
        assertEquals(body.length, OtpNotificationPolicy.redact(body).length)
    }
}
