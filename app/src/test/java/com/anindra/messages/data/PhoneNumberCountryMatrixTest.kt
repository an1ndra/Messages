package com.anindra.messages.data

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Issue #444, for every region libphonenumber knows (not just the big markets).
 *
 * The fix is country-general: libphonenumber's metadata owns the trunk prefix
 * and the country code, so there is no per-country branch. This test proves it
 * by taking each region's own example number and checking that its national and
 * E.164 spellings are the same identity — on a device in that region and on a
 * device whose region is somewhere else (the reporter's case).
 */
class PhoneNumberCountryMatrixTest {

    private val util = PhoneNumberUtil.getInstance()

    private fun example(region: String): Phonenumber.PhoneNumber? =
        util.getExampleNumberForType(region, PhoneNumberUtil.PhoneNumberType.MOBILE)
            ?: util.getExampleNumberForType(region, PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE)
            ?: util.getExampleNumber(region)

    private fun <T> withRegion(country: String, block: () -> T): T {
        val previous = Locale.getDefault()
        return try {
            Locale.setDefault(Locale("", country))
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun nationalAndInternationalFormsAgreeInEveryRegion() {
        val failures = mutableListOf<String>()
        var covered = 0

        for (region in util.supportedRegions) {
            if (region.length != 2 || region == "001") continue
            val number = example(region) ?: continue
            val e164 = util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
            val national = util.format(number, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
            covered++

            withRegion(region) {
                val canonical = AddressIdentity.canonical(national, region)
                if (canonical != e164) {
                    failures += "$region: canonical(\"$national\") = $canonical, expected $e164"
                }
                if (!AddressIdentity.samePerson(national, e164)) {
                    failures += "$region: samePerson(\"$national\", \"$e164\") = false"
                }
            }

            if (region != "US") {
                withRegion("US") {
                    if (!AddressIdentity.samePerson(national, e164)) {
                        failures += "$region on a US device: samePerson(\"$national\", \"$e164\") = false"
                    }
                }
            }
        }

        assertTrue("no regions were exercised", covered > 150)
        assertTrue(
            "failing regions:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }
}
