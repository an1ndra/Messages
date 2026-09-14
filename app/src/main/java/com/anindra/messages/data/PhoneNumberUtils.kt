package com.anindra.messages.data

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Canonical phone-number identity (E.164) and locale-aware display formatting,
 *  backed by libphonenumber — the same normalization Google Messages applies to
 *  its participants table. The app stores one canonical spelling per person and
 *  derives the display string from it, so "(555) 123-4567", "555-123-4567" and
 *  "+15551234567" all resolve to the same thread.
 *
 *  Performance: libphonenumber parsing is the slow part (~ms), so both
 *  [toE164] and [displayFor] are cached in-process; each unique number is
 *  parsed once and the result is persisted in the participants table. */
object PhoneNumberUtils {

    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    @Volatile
    private var deviceRegion: String = ""

    private const val MISS = "∅"
    private val e164Cache = ConcurrentHashMap<String, String>()
    private val displayCache = ConcurrentHashMap<String, String>()

    /** Regions sharing the NANP country code (1): US, CA, PR, … */
    private val nanpRegions: Set<String> by lazy {
        util.supportedRegions.filterTo(mutableSetOf()) { util.getCountryCodeForRegion(it) == 1 }
    }

    /** Resolves and caches the device region (SIM → active SIM → locale).
     *  Call once at startup; [region] falls back to the locale until then. */
    fun init(context: Context) {
        deviceRegion = resolveRegion(context)
    }

    fun region(): String =
        if (deviceRegion.isBlank()) Locale.getDefault().country.uppercase() else deviceRegion

    private fun resolveRegion(context: Context): String {
        try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                .simCountryIso?.takeIf { it.isNotBlank() }?.let { return it.uppercase() }
        } catch (_: Exception) {
        }
        try {
            // Needs READ_PHONE_STATE; falls through to the locale when denied.
            val sm = context.getSystemService(SubscriptionManager::class.java)
            sm?.activeSubscriptionInfoList?.firstOrNull()
                ?.countryIso?.takeIf { it.isNotBlank() }?.let { return it.uppercase() }
        } catch (_: Exception) {
        }
        return Locale.getDefault().country.uppercase()
    }

    /** Heuristic gate: dialable digits only, 4–15 long, no letters — excludes
     *  alphanumeric sender IDs (DK-AIRCEL, VM-HDFCBK) and short codes from
     *  expensive parsing. */
    fun isLikelyPhoneNumber(input: String): Boolean {
        var digits = 0
        for (c in input) {
            when {
                c.isDigit() -> digits++
                c.isLetter() -> return false
            }
        }
        return digits in 4..15
    }

    /** Canonical E.164 for any spelling of [input] ("+1 555 123-4567",
     *  "(555) 123-4567", "5551234567" → "+15551234567"), or null when the input
     *  cannot be resolved to a number (alphanumeric ID, ambiguous local form).
     *  Gated on isPossibleNumber — not isValidNumber — so reserved/fictional
     *  ranges (555-01xx, emulator numbers) and carrier-specific ranges still
     *  canonicalize; strict validity is not useful for thread identity.
     *  Results are cached; parsing happens once per unique input+region. */
    fun toE164(input: String, region: String): String? {
        if (!isLikelyPhoneNumber(input)) return null
        val cleaned = input.replace(Regex("[^0-9+]"), "")
        val reg = region.uppercase()
        val key = "$cleaned|$reg"
        e164Cache[key]?.let { return if (it == MISS) null else it }
        val result = try {
            val number = util.parse(cleaned, reg)
            if (util.isPossibleNumber(number)) {
                util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
        e164Cache[key] = result ?: MISS
        return result
    }

    /** UI form of a canonical (or raw) number: NATIONAL formatting when the
     *  number belongs to [region], INTERNATIONAL otherwise. NANP numbers whose
     *  region libphonenumber can't pin down (reserved ranges) count as local on
     *  NANP devices. Returns the input unchanged for non-numeric addresses. */
    fun displayFor(input: String, region: String): String {
        if (!isLikelyPhoneNumber(input)) return input
        val reg = region.uppercase()
        val e164 = toE164(input, reg) ?: return input
        val key = "$e164|$reg"
        displayCache[key]?.let { return it }
        val out = try {
            val number = util.parse(e164, null)
            var numberRegion = util.getRegionCodeForNumber(number)
            if ((numberRegion == "ZZ" || numberRegion == null) && number.countryCode == 1 && reg in nanpRegions) {
                numberRegion = reg
            }
            val format = if (numberRegion == reg) {
                PhoneNumberUtil.PhoneNumberFormat.NATIONAL
            } else {
                PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
            }
            util.format(number, format)
        } catch (_: Exception) {
            e164
        }
        displayCache[key] = out
        return out
    }

    /** ISO 3166-1 alpha-2 country code for an E.164 number, or "" when unknown. */
    fun regionFor(e164: String): String = try {
        val code = util.getRegionCodeForNumber(util.parse(e164, null))
        if (code == "ZZ" || code == null) "" else code
    } catch (_: Exception) {
        ""
    }
}
