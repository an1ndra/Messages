package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
