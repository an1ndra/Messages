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
    val maxMessageBytes: Int?,
    /**
     * Whether the SIM is attached to a network at all. Taken from the SIM's
     * MCC/MNC, which is blank when it has none.
     */
    val hasNetwork: Boolean = true
) {
    /**
     * The verdict reads the carrier's own MMS flag and nothing else.
     *
     * The country code and region are carried alongside as context, but they
     * are not capability facts: two carriers in one country disagree, and the
     * region is often a guess derived from the number. Stock Messages likewise
     * keys the whole thing off `enabledMMS`. The one deliberate divergence is
     * that a carrier which never published the flag reads as UNKNOWN rather
     * than supported -- there is no server-side override layer here to patch a
     * carrier's silence, so silence cannot be read as consent.
     */
    val verdict: MmsVerdict
        get() = when {
            !hasNetwork -> MmsVerdict.NO_CARRIER
            mmsEnabledByCarrier == null -> MmsVerdict.UNKNOWN
            !mmsEnabledByCarrier -> MmsVerdict.CARRIER_DISABLED
            else -> MmsVerdict.SUPPORTED
        }

    val supported: Boolean get() = verdict == MmsVerdict.SUPPORTED
}

/** Carrier MMS flag, narrowed to what the verdict needs so it can be faked. */
interface CarrierMmsValues {
    /** Whether the carrier config states MMS on/off at all. */
    fun hasMmsKey(): Boolean
    fun mmsEnabled(): Boolean
}

object SimMmsVerdict {
    /**
     * A carrier whose config omits the MMS key has not said MMS works. Reading
     * the absent key with a `true` fallback reported "Supported" for exactly
     * the carriers that never declared it, which is the common case. Silence is
     * [MmsVerdict.UNKNOWN], not consent.
     */
    fun carrierEnabled(values: CarrierMmsValues?): Boolean? = when {
        values == null -> null
        !values.hasMmsKey() -> null
        else -> values.mmsEnabled()
    }
}

/** Country calling code ("+44") and ISO region ("GB") for a SIM's number, or
 *  the SIM's own network country when the number is not exposed. */
object SimMmsCountry {
    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    /**
     * A carrier-supplied SIM number is national: it carries no country of its
     * own. Prepending a "+" to it is worse than reporting nothing, because the
     * digits then read as whichever country happens to claim them — an Indian
     * 8633333333 becomes China's +86 and 9876543210 becomes Iran's +98. So a
     * number states its own country only when it already says "+"; otherwise it
     * can only be placed against [networkRegion], the SIM's own country.
     */
    private fun statesItsOwnCountry(number: String): Boolean =
        number.trim().startsWith("+")

    private fun usableRegion(networkRegion: String?): String? =
        networkRegion?.trim()?.uppercase()?.takeIf { it.length == 2 && it != "ZZ" }

    /** Calling code of [number] including the plus, or null when it is not one.
     *  [networkRegion] is the SIM's ISO country, used only for national numbers. */
    fun callingCodeOf(number: String?, networkRegion: String? = null): String? {
        val raw = number?.trim().orEmpty()
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val region = usableRegion(networkRegion)
        if (!statesItsOwnCountry(raw) && region == null) return null
        return try {
            val parsed = if (statesItsOwnCountry(raw)) util.parse(raw, null)
            else util.parse(digits, region)
            if (parsed.countryCode <= 0) null else "+${parsed.countryCode}"
        } catch (_: Exception) {
            null
        }
    }

    /** ISO 3166-1 alpha-2 region for [number], or "" when libphone cannot place
     *  it. A region whose own country calling code disagrees with the number's
     *  is rejected: reserved ranges such as NANP 555 are attributed to a country
     *  that does not use that code at all, and the caller falls back to the
     *  SIM's own network country rather than trusting it.
     *
     *  A national number is placed against [networkRegion]; it names no country
     *  itself, so without the SIM's country there is nothing to report. */
    fun regionOf(number: String?, networkRegion: String? = null): String {
        val raw = number?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val region = usableRegion(networkRegion)
        if (!statesItsOwnCountry(raw) && region == null) return ""
        return try {
            val parsed = if (statesItsOwnCountry(raw)) util.parse(raw, null)
            else util.parse(raw.filter { it.isDigit() }, region)
            val found = util.getRegionCodeForNumber(parsed)
            when {
                found.isNullOrBlank() || found == "ZZ" -> ""
                util.getCountryCodeForRegion(found) != parsed.countryCode -> ""
                else -> found
            }
        } catch (_: Exception) {
            ""
        }
    }
}
