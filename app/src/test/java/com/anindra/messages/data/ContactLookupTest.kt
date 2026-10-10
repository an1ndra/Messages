package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The profile screen offers "Info" instead of "Contact" when the number is
 * already saved, so the lookup has to match a conversation's address against
 * however that address happens to be stored in Contacts.
 */
class ContactLookupTest {

    @Test
    fun identicalNumbersMatch() {
        assertTrue(ContactLookup.compareDigits("+15558887777", "+15558887777"))
    }

    @Test
    fun formattingDifferencesStillMatch() {
        // Stored "+1 555 888 7777" vs conversation address "+15558887777".
        assertTrue(ContactLookup.compareDigits("+1 555 888 7777", "+15558887777"))
    }

    @Test
    fun countryCodeDifferenceStillMatches() {
        // Same subscriber, different country code prefix.
        assertTrue(ContactLookup.compareDigits("+15558887777", "+44558887777"))
    }

    @Test
    fun differentNumbersDoNotMatch() {
        assertFalse(ContactLookup.compareDigits("+15558887777", "+15559998888"))
    }

    @Test
    fun emptyOrBlankNeverMatches() {
        // A blank stored row must not be treated as a match, or every
        // conversation would look like it is already saved.
        assertFalse(ContactLookup.compareDigits(null, "+15558887777"))
        assertFalse(ContactLookup.compareDigits("+15558887777", null))
        assertFalse(ContactLookup.compareDigits("", "+15558887777"))
        assertFalse(ContactLookup.compareDigits("   ", "+15558887777"))
    }

    @Test
    fun nonNumericAddressesNeverMatch() {
        // Email-like or alphanumeric senders are not in Contacts.
        assertFalse(ContactLookup.compareDigits("somebody@example.com", "+15558887777"))
        assertFalse(ContactLookup.compareDigits("ABC123", "ABC123"))
    }

    @Test
    fun shortNumbersMatchThemselves() {
        // Fewer than seven digits cannot be disambiguated, but an exact
        // repetition should still be recognised.
        assertTrue(ContactLookup.compareDigits("5551234", "5551234"))
    }

    @Test
    fun canonicalFallsBackToDigitsForUnparseableNumbers() {
        // libphonenumber cannot parse this, but the digits still identify it.
        val c = ContactLookup.canonical("+1-555-000-000000000")
        assertTrue("expected digits fallback, got $c", c != null && c.all { it.isDigit() })
    }
}
