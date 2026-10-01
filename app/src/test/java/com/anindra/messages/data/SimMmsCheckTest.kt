package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimMmsCheckTest {

    private fun check(
        region: String? = "GB",
        carrierMms: Boolean? = true,
        number: String? = null,
        callingCode: String? = "+44"
    ) = SimMmsCheck(
        subscriptionId = 1,
        slotIndex = 0,
        carrierName = "Vodafone",
        number = number,
        callingCode = callingCode,
        region = region,
        mmsEnabledByCarrier = carrierMms,
        maxMessageBytes = MmsConfig.DEFAULT_MAX_MESSAGE_SIZE
    )

    @Test
    fun aCarrierWithMmsOnIsSupported() {
        assertEquals(MmsVerdict.SUPPORTED, check().verdict)
        assertTrue(check().supported)
    }

    @Test
    fun aCarrierThatTurnsMmsOffIsNotSupported() {
        assertEquals(MmsVerdict.CARRIER_DISABLED, check(carrierMms = false).verdict)
        assertFalse(check(carrierMms = false).supported)
    }

    @Test
    fun anUnreadableCarrierConfigIsUnknownRatherThanUnsupported() {
        // Guessing "no" here would tell the user their carrier cannot send MMS
        // when in fact the check simply did not run.
        val result = check(carrierMms = null)
        assertEquals(MmsVerdict.UNKNOWN, result.verdict)
        assertFalse(result.supported)
    }

    @Test
    fun aSimWithNoNetworkCannotBeChecked() {
        assertEquals(MmsVerdict.NO_CARRIER, check(region = null).verdict)
        assertEquals(MmsVerdict.NO_CARRIER, check(region = "").verdict)
    }

    @Test
    fun carrierOffBeatsAMissingRegion() {
        // The carrier's own answer is the more specific one, so it wins over the
        // "nothing to check" case.
        assertEquals(MmsVerdict.CARRIER_DISABLED, check(region = null, carrierMms = false).verdict)
    }

    @Test
    fun callingCodeComesFromTheNumber() {
        assertEquals("+44", SimMmsCountry.callingCodeOf("+447911123456"))
        assertEquals("+1", SimMmsCountry.callingCodeOf("+15551234567"))
        assertEquals("+91", SimMmsCountry.callingCodeOf("+919876543210"))
    }

    @Test
    fun aNumberWithNoCountryIsNotForced() {
        assertNull(SimMmsCountry.callingCodeOf(null))
        assertNull(SimMmsCountry.callingCodeOf(""))
        assertNull(SimMmsCountry.callingCodeOf("Vodafone"))
    }

    @Test
    fun regionComesFromTheNumberWhenThereIsOne() {
        assertEquals("GB", SimMmsCountry.regionOf("+442071838750"))
        assertEquals("FR", SimMmsCountry.regionOf("+33612345678"))
        assertEquals("IN", SimMmsCountry.regionOf("+919876543210"))
    }

    @Test
    fun aSharedCallingCodeReportsTheRegionTheRangeBelongsTo() {
        // +44 covers both the UK and Guernsey, so the region shown is the one
        // the number range is actually registered in.
        assertEquals("+44", SimMmsCountry.callingCodeOf("+447911123456"))
        assertTrue(SimMmsCountry.regionOf("+447911123456").startsWith("G"))
    }

    @Test
    fun aReservedRangeIsNotAttributedToACountryItDoesNotBelongTo() {
        // libphone places NANP 555 in a country that does not use country code 1.
        // Reporting that as the SIM's country would be worse than reporting none.
        assertEquals("", SimMmsCountry.regionOf("+15551234567"))
    }

    @Test
    fun aWithheldNumberLeavesTheRegionEmptyRatherThanGuessed() {
        // The caller falls back to the SIM's own network country; guessing a
        // region from the SIM's carrier name would be a fiction.
        assertEquals("", SimMmsCountry.regionOf(null))
        assertEquals("", SimMmsCountry.regionOf(""))
    }
}
