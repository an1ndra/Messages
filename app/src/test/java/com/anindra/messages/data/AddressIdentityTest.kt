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
    fun foreignTrunkZeroIsNotFabricated() {
        // A national number whose trunk '0' the assumed region did not consume
        // must not be rewritten into a bogus "+10..." destination (#444).
        assertEquals("0701234567", AddressIdentity.canonical("0701234567", "US"))
        // When the region is right the parse still happens.
        assertEquals("+46701234567", AddressIdentity.canonical("0701234567", "SE"))
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
    fun nationalAndInternationalFormsMergeThroughTheCountryPlan() {
        // Issue #444: the national form keeps a trunk prefix that the E.164
        // form drops. libphonenumber knows each country's trunk rule.
        withRegion("SE") {
            assertTrue(AddressIdentity.samePerson("0700000002", "+46700000002"))
        }
        withRegion("IN") {
            assertTrue(AddressIdentity.samePerson("09876543210", "+919876543210"))
        }
        withRegion("KR") {
            assertTrue(AddressIdentity.samePerson("01012345678", "+821012345678"))
        }
        withRegion("GB") {
            assertTrue(AddressIdentity.samePerson("07911123456", "+447911123456"))
        }
        // The US has no trunk prefix, so its national form is already a match.
        withRegion("US") {
            assertTrue(AddressIdentity.samePerson("5551234567", "+15551234567"))
        }
    }

    @Test
    fun foreignNationalFormsMergeOnAnyDeviceRegion() {
        // The device region is US, yet libphonenumber's own metadata resolves
        // each country's trunk prefix without a per-country rule.
        withRegion("US") {
            assertTrue(AddressIdentity.samePerson("89161234567", "+79161234567"))   // Russia, trunk 8
            assertTrue(AddressIdentity.samePerson("06301234567", "+36301234567"))   // Hungary, trunk 06
            assertTrue(AddressIdentity.samePerson("015112345678", "+4915112345678")) // Germany, trunk 0
        }
    }

    @Test
    fun sameDigitsInAnotherCountryAreNotTheSamePerson() {
        // 0700000002 is a valid Swedish mobile but only a "possible" Swiss
        // number; +41 must not capture the Swedish form (or vice versa).
        withRegion("US") {
            assertFalse(AddressIdentity.samePerson("0700000002", "+41700000002"))
            assertFalse(AddressIdentity.samePerson("+41700000002", "0700000002"))
            assertTrue(AddressIdentity.samePerson("0700000002", "+46700000002"))
        }
    }

    @Test
    fun differentCountriesWithTheSameTailDoNotMerge() {
        // The device's own numbering plan wins when it accepts the national
        // form, so a foreign number that merely shares a tail is not merged.
        withRegion("IN") {
            assertFalse(AddressIdentity.samePerson("0700000002", "+46700000002"))
        }
        withRegion("US") {
            assertFalse(AddressIdentity.samePerson("0700000002", "+15550000002"))
            assertFalse(AddressIdentity.samePerson("+46700000002", "+15550000002"))
            assertFalse(AddressIdentity.samePerson("5551234567", "+46700000002"))
        }
    }

    /** Runs [block] with the JVM default locale set to [country], which is what
     *  [PhoneNumberUtils.region] falls back to when no SIM region was resolved. */
    private fun withRegion(country: String, block: () -> Unit) {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("", country))
            block()
        } finally {
            java.util.Locale.setDefault(previous)
        }
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
        assertFalse(AddressIdentity.matchesNumber("ABC123", "123"))
    }

    @Test
    fun alphanumericSenderNeverMatchesAnyNumberQuery() {
        // Issue #284: stripping the digits out of "X1 INFO" left "1", and the
        // reverse endsWith test then flickered on and off as the user typed —
        // shown for 101, gone for 1010, back for 10101. A sender ID is not a
        // number, so it must not match at any length.
        for (sender in listOf("X1 INFO", "A1 SRB", "DK-AIRCEL", "VM-HDFCBK", "ABC123")) {
            for (query in listOf("1", "10", "101", "1010", "10101", "101010")) {
                assertFalse("\"$sender\" matched \"$query\"", AddressIdentity.matchesNumber(sender, query))
            }
        }
    }

    @Test
    fun isNumberQueryAcceptsOnlyNumberShapedQueries() {
        assertTrue(AddressIdentity.isNumberQuery("077"))
        assertTrue(AddressIdentity.isNumberQuery("+44 7700 900123"))
        assertTrue(AddressIdentity.isNumberQuery("(555) 123-4567"))
        assertTrue(AddressIdentity.isNumberQuery("101"))
        assertFalse(AddressIdentity.isNumberQuery("abc"))
        assertFalse(AddressIdentity.isNumberQuery("7pm"))
        assertFalse(AddressIdentity.isNumberQuery("077a"))
        assertFalse(AddressIdentity.isNumberQuery("abc123"))
        assertFalse(AddressIdentity.isNumberQuery(""))
        assertFalse(AddressIdentity.isNumberQuery(" "))
    }

    @Test
    fun aNumberIsFoundFromItsFirstDigits() {
        // Issue #284: a suffix test cannot succeed until the query is nearly
        // complete, so the contact only appeared at the last digit. The thread
        // stores E.164 while the contact is typed nationally, and neither digit
        // run is a suffix of the other — containment has to bridge that.
        assertTrue(AddressIdentity.matchesNumber("+447700900123", "077"))
        assertTrue(AddressIdentity.matchesNumber("+447700900123", "07700"))
        assertTrue(AddressIdentity.matchesNumber("+447700900123", "0770090012"))
        assertTrue(AddressIdentity.matchesNumber("+919876543210", "09876"))
        assertFalse(AddressIdentity.matchesNumber("+447700900123", "0770090099"))
    }

    @Test
    fun trunkZeroNationalFormsStillMatch() {
        // Italian and German numbers keep a leading zero in the national part,
        // and the stored address has neither the country code nor that zero, so
        // only the national digit run can match them.
        // Italy keeps no trunk zero in the national form (mobile numbers start
        // at 3), so its national run is the significant digits alone.
        assertTrue(AddressIdentity.matchesNumber("+393331234567", "3331234567"))
        assertTrue(AddressIdentity.matchesNumber("+393331234567", "3331"))
        // Germany and India do keep one, and it is dropped from the stored E.164.
        assertTrue(AddressIdentity.matchesNumber("+4915112345678", "015112345678"))
        assertTrue(AddressIdentity.matchesNumber("+4915112345678", "15112345678"))
        assertTrue(AddressIdentity.matchesNumber("+919876543210", "09876543210"))
        assertTrue(AddressIdentity.matchesNumber("+919876543210", "0987"))
        assertFalse(AddressIdentity.matchesNumber("+393331234567", "3331234999"))
    }

    @Test
    fun reverseEndsWithNeedsEnoughDigitsOnTheAddress() {
        // The query can be longer than the address (a number typed in full
        // against a locally-stored short form), but only once the address holds
        // enough digits to be a real number rather than a stripped-down ID.
        assertTrue(AddressIdentity.matchesNumber("5550001234", "+15550001234"))
        assertFalse(AddressIdentity.matchesNumber("1234", "+15550001234"))
    }

    @Test
    fun tooShortToBeNumberHoldsTheListStill() {
        assertTrue(AddressIdentity.tooShortToBeNumber("1"))
        assertTrue(AddressIdentity.tooShortToBeNumber("10"))
        assertFalse(AddressIdentity.tooShortToBeNumber("101"))
        // Text is never a number query, however short.
        assertFalse(AddressIdentity.tooShortToBeNumber("a"))
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

    @Test
    fun shortServiceCodesAreReplyable() {
        assertTrue(AddressIdentity.isReplyable("198"))
        assertTrue(AddressIdentity.isReplyable("199"))
        assertTrue(AddressIdentity.isReplyable("112"))
        // Identity for a short code is kept verbatim because it is not parsed.
        assertEquals("198", AddressIdentity.canonical("198", "IN"))
        assertEquals("199", AddressIdentity.canonical("199", "IN"))
    }
}
