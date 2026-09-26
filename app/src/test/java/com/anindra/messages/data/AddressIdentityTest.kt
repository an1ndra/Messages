package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressIdentityTest {

    @Test
    fun alphanumericSenderIdIsPreservedNotReducedToDigits() {
        assertEquals("A1 SRB", AddressIdentity.canonical("A1 SRB", "RS"))
        assertEquals("DK-AIRCEL", AddressIdentity.canonical("DK-AIRCEL", "IN"))
        assertEquals("VM-HDFCBK", AddressIdentity.canonical("VM-HDFCBK", "IN"))
        assertEquals("AX-KOTAKB-S", AddressIdentity.canonical("AX-KOTAKB-S", "IN"))
        assertEquals("DK-TEST99", AddressIdentity.canonical("DK-TEST99", "IN"))
    }

    @Test
    fun phoneNumbersStillCanonicalizeToE164() {
        assertEquals("+15551234567", AddressIdentity.canonical("(555) 123-4567", "US"))
        assertEquals("+15551234567", AddressIdentity.canonical("+1 555 123 4567", "US"))
        assertEquals("+919876543210", AddressIdentity.canonical("9876543210", "IN"))
        assertEquals("+15551234567", AddressIdentity.canonical("5551234567", "US"))
    }

    @Test
    fun alphanumericSenderNeverMatchesItsDigits() {
        assertFalse(AddressIdentity.samePerson("A1 SRB", "1"))
        assertFalse(AddressIdentity.samePerson("DK-AIRCEL", "99"))
        assertFalse(AddressIdentity.samePerson("DK-TEST99", "99"))
        assertFalse(AddressIdentity.samePerson("AX-KOTAKB-S", "1"))
    }

    @Test
    fun alphanumericSenderMatchesCaseInsensitiveExact() {
        assertTrue(AddressIdentity.samePerson("A1 SRB", "a1 srb"))
        assertTrue(AddressIdentity.samePerson("AX-KOTAKB-S", "ax-kotakb-s"))
        assertTrue(AddressIdentity.samePerson("VM-HDFCBK", "vm-hdfcbk"))
    }

    @Test
    fun distinctAlphanumericSendersStayDistinct() {
        assertFalse(AddressIdentity.samePerson("AX-KOTAKB-S", "AX-HDFCBK"))
        assertFalse(AddressIdentity.samePerson("DK-AIRCEL", "VM-HDFCBK"))
    }

    @Test
    fun numberSpellingsStillMerge() {
        assertTrue(AddressIdentity.samePerson("+15551234567", "5551234567"))
        assertTrue(AddressIdentity.samePerson("+919876543210", "9876543210"))
        assertTrue(AddressIdentity.samePerson("+15551234567", "+1 555 123 4567"))
    }

    @Test
    fun replyableOnlyForDialableNumbers() {
        assertFalse(AddressIdentity.isReplyable("A1 SRB"))
        assertFalse(AddressIdentity.isReplyable("DK-AIRCEL"))
        assertFalse(AddressIdentity.isReplyable("AX-KOTAKB-S"))
        assertFalse(AddressIdentity.isReplyable("DK-TEST99"))
        assertTrue(AddressIdentity.isReplyable("+15551234567"))
        assertTrue(AddressIdentity.isReplyable("+919876543210"))
    }
}
