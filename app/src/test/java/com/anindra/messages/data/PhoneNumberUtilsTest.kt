package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberUtilsTest {

    @Test
    fun franceNationalAndInternationalCollapseToSameE164() {
        val national = PhoneNumberUtils.toE164("06 12 34 56 78", "FR")
        val international = PhoneNumberUtils.toE164("+33 6 12 34 56 78", "FR")
        val compact = PhoneNumberUtils.toE164("+33612345678", "FR")
        assertEquals("+33612345678", national)
        assertEquals(national, international)
        assertEquals(national, compact)
    }

    @Test
    fun otherRegionsMatchTheirInternationalSpelling() {
        assertEquals("+919876543210", PhoneNumberUtils.toE164("9876543210", "IN"))
        assertEquals("+919876543210", PhoneNumberUtils.toE164("+91 98765 43210", "IN"))
        assertEquals("+447911123456", PhoneNumberUtils.toE164("07911 123456", "GB"))
        assertEquals("+447911123456", PhoneNumberUtils.toE164("+44 7911 123456", "GB"))
    }

    @Test
    fun usFormatsMatch() {
        assertEquals("+15551234567", PhoneNumberUtils.toE164("(555) 123-4567", "US"))
        assertEquals("+15551234567", PhoneNumberUtils.toE164("555-123-4567", "US"))
        assertEquals("+15551234567", PhoneNumberUtils.toE164("+1 555 123 4567", "US"))
    }

    @Test
    fun alphanumericSenderIsNotANumber() {
        assertNull(PhoneNumberUtils.toE164("DK-AIRCEL", "FR"))
        assertNull(PhoneNumberUtils.toE164("VM-HDFCBK", "IN"))
    }

    @Test
    fun shortServiceCodesAreDialableButNotE164Parsed() {
        assertTrue(PhoneNumberUtils.isDialableAddress("198"))
        assertTrue(PhoneNumberUtils.isDialableAddress("199"))
        assertTrue(PhoneNumberUtils.isDialableAddress("112"))
        // 3-digit numbers stay outside the E.164 parse gate so the address
        // survives verbatim instead of being rewritten by libphonenumber.
        assertFalse(PhoneNumberUtils.isLikelyPhoneNumber("198"))
        assertNull(PhoneNumberUtils.toE164("198", "IN"))
        assertNull(PhoneNumberUtils.toE164("199", "IN"))
    }

    @Test
    fun alphanumericSenderIsNotDialable() {
        assertFalse(PhoneNumberUtils.isDialableAddress("DK-AIRCEL"))
        assertFalse(PhoneNumberUtils.isDialableAddress("VM-HDFCBK"))
        assertFalse(PhoneNumberUtils.isDialableAddress("A1 SRB"))
    }
}
