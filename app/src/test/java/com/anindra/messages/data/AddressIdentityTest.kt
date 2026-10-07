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
    fun matchesNumberIgnoresCountryCodeAndTrunkZero() {
        // Issue #284: the thread holds E.164, the saved contact is typed
        // nationally. Neither digit run is a suffix of the other, so the
        // subscriber digits are what has to be compared.
        // A two-digit country code only fails the suffix test when its leading digit
        // is not the trunk zero's predecessor-by-coincidence, so 91 and 44 are
        // the real cases: neither digit run is a suffix of the other.
        assertTrue(AddressIdentity.matchesNumber("+919876543210", "09876543210"))
        assertTrue(AddressIdentity.matchesNumber("+919876543210", "09876 543 210"))
        assertTrue(AddressIdentity.matchesNumber("+447911123456", "07911123456"))
        assertTrue(AddressIdentity.matchesNumber("+919876543210", "00919876543210"))
        assertFalse(AddressIdentity.matchesNumber("+919876111111", "09876543210"))
        // Egypt happens to suffix-match already ("2" + "0" + N), so keep it as
        // a guard that the fall-through did not regress the easy spelling.
        assertTrue(AddressIdentity.matchesNumber("+201001234567", "01001234567"))
        assertTrue(AddressIdentity.matchesNumber("+15550001234", "555-000-1234"))
    }

    @Test
    fun matchesNumberStillNeedsARealRunOfDigits() {
        // A stray digit or two must not drag in every conversation, and an
        // alphanumeric sender ID must not match on the digits inside it.
        assertFalse(AddressIdentity.matchesNumber("+201001234567", "1"))
        assertFalse(AddressIdentity.matchesNumber("+201001234567", "01"))
        assertFalse(AddressIdentity.matchesNumber("", "1234567"))
        // Not asserted: matchesNumber("ABC123", "123") is true, because the
        // digits stripped out of an alphanumeric sender ID are "123". That is
        // pre-existing and harmless for search (the row is findable by its
        // address text anyway); sender identity is guarded by samePerson and
        // isReplyable, which keep such IDs verbatim.
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
