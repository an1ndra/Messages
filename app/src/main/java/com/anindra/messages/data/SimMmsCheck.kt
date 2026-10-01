package com.anindra.messages.data

import com.google.i18n.phonenumbers.PhoneNumberUtil

/** Why a SIM's MMS verdict came out the way it did. */
enum class MmsVerdict {
    /** The carrier has MMS switched on for this subscription. */
    SUPPORTED,

    /** The carrier explicitly reports MMS as off. */
    CARRIER_DISABLED,

    /** The SIM carries no network (no MCC/MNC), so there is nothing to check. */
    NO_CARRIER,

    /** The carrier configuration could not be read. */
    UNKNOWN
}

/**
 * One SIM's MMS capability.
 *
 * The number's country calling code and region are what the check is *about* —
 * they say which country the SIM belongs to — but libphone carries no data on
 * whether a country's carriers actually run an MMS service. The verdict
 * therefore comes from the carrier configuration Android holds for that
 * subscription, which is the only authoritative answer available on the device.
 *
 * A SIM that does not expose its number (most carriers withhold it, and it
 * needs a permission this app does not ask for) is not a failure: the SIM's own
 * network country is used instead and [number] stays null.
 */
data class SimMmsCheck(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String?,
    val number: String?,
    val callingCode: String?,
    val region: String?,
    val mmsEnabledByCarrier: Boolean?,
    val maxMessageBytes: Int?
) {
    val verdict: MmsVerdict
        get() = when {
            mmsEnabledByCarrier == null -> MmsVerdict.UNKNOWN
            !mmsEnabledByCarrier -> MmsVerdict.CARRIER_DISABLED
            region.isNullOrBlank() -> MmsVerdict.NO_CARRIER
            else -> MmsVerdict.SUPPORTED
        }

    val supported: Boolean get() = verdict == MmsVerdict.SUPPORTED
}

/** Country calling code ("+44") and ISO region ("GB") for a SIM's number, or
 *  the SIM's own network country when the number is not exposed. */
object SimMmsCountry {
    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    private val PLUS = Regex("^\\+?[0-9]{1,4}$")

    /** Calling code of [number] including the plus, or null when it is not one. */
    fun callingCodeOf(number: String?): String? {
        val digits = number?.filter { it.isDigit() }.orEmpty()
        if (digits.isEmpty() || !PLUS.matches(digits.take(4))) return null
        return try {
            val code = util.parse("+$digits", null).countryCode
            if (code <= 0) null else "+$code"
        } catch (_: Exception) {
            null
        }
    }

    /** ISO 3166-1 alpha-2 region for [number], or "" when libphone cannot place
     *  it. A region whose own country calling code disagrees with the number's
     *  is rejected: reserved ranges such as NANP 555 are attributed to a country
     *  that does not use that code at all, and the caller falls back to the
     *  SIM's own network country rather than trusting it. */
    fun regionOf(number: String?): String {
        if (number.isNullOrBlank()) return ""
        return try {
            val parsed = util.parse(number.trim(), null)
            val region = util.getRegionCodeForNumber(parsed)
            when {
                region.isNullOrBlank() || region == "ZZ" -> ""
                util.getCountryCodeForRegion(region) != parsed.countryCode -> ""
                else -> region
            }
        } catch (_: Exception) {
            ""
        }
    }
}
