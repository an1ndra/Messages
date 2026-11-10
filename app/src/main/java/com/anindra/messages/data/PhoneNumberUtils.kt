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

    @Volatile
    private var appContext: Context? = null

    private const val MISS = "∅"
    private val e164Cache = ConcurrentHashMap<String, String>()
    private val displayCache = ConcurrentHashMap<String, String>()
    private val nationalCache = ConcurrentHashMap<String, String>()
    private val regionCache = ConcurrentHashMap<String, String>()
    private val significantCache = ConcurrentHashMap<String, String>()
    private val validityCache = ConcurrentHashMap<String, String>()

    /** Regions sharing the NANP country code (1): US, CA, PR, … */
    private val nanpRegions: Set<String> by lazy {
        util.supportedRegions.filterTo(mutableSetOf()) { util.getCountryCodeForRegion(it) == 1 }
    }

    /** Resolves and caches the device region (SIM → active SIM → locale).
     *  Call once at startup; [region] falls back to the locale until then. */
    fun init(context: Context) {
        appContext = context.applicationContext
        deviceRegion = resolveRegion(context)
    }

    fun region(): String {
        if (deviceRegion.isNotBlank()) return deviceRegion
        // The SIM may not have been ready at init(); retry now that the caller
        // actually needs a region.
        val ctx = appContext
        if (ctx != null) {
            val resolved = resolveRegion(ctx)
            if (resolved.isNotBlank()) {
                deviceRegion = resolved
                return deviceRegion
            }
        }
        val locale = Locale.getDefault().country.uppercase()
        return if (locale.isNotBlank()) locale else deviceRegion
    }

    private fun resolveRegion(context: Context): String {
        // SIM country (no permission needed). Trim and validate length.
        try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                .simCountryIso?.trim()?.takeIf { it.length == 2 }?.let { return it.uppercase() }
        } catch (_: Exception) {
        }
        // Network country (no permission needed) - often available even when the
        // SIM country iso is empty (some OEMs / Android 13).
        try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                .networkCountryIso?.trim()?.takeIf { it.length == 2 }?.let { return it.uppercase() }
        } catch (_: Exception) {
        }
        try {
            // Needs READ_PHONE_STATE; falls through to the locale when denied.
            val sm = context.getSystemService(SubscriptionManager::class.java)
            sm?.activeSubscriptionInfoList?.firstOrNull { !it.countryIso.isNullOrBlank() }
                ?.countryIso?.trim()?.takeIf { it.length == 2 }?.let { return it.uppercase() }
        } catch (_: SecurityException) {
            // READ_PHONE_STATE revoked at runtime; falls through to the locale.
        } catch (_: Exception) {
        }
        val locale = Locale.getDefault().country.trim().uppercase()
        return if (locale.length == 2) locale else ""
    }

    /** True for a dialable numeric address — digits only, 3–15 long, no
     *  letters. The 3-digit floor admits service/short codes such as India's
     *  198/199 or 112, so they stay replyable. For the stricter E.164-parse
     *  gate see [isLikelyPhoneNumber]. */
    fun isDialableAddress(input: String): Boolean {
        val digits = digitRun(input) ?: return false
        return digits in 3..15
    }

    /** Parse gate for [toE164]: digits only, 4–15 long, no letters. Kept at 4
     *  so short codes are never fed to libphonenumber, which cannot resolve
     *  them, and survive verbatim as identity. */
    fun isLikelyPhoneNumber(input: String): Boolean {
        val digits = digitRun(input) ?: return false
        return digits in 4..15
    }

    private fun digitRun(input: String): Int? {
        var digits = 0
        for (c in input) {
            when {
                c.isDigit() -> digits++
                c.isLetter() -> return null
            }
        }
        return digits
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

    /** The digits of [input] with the country code stripped ("+447700900123" →
     *  "07700900123"). Search needs this because a thread stores its address in
     *  E.164 while the number a contact is dialled and typed as is national:
     *  neither digit run is a suffix of the other, so no suffix test can match
     *  until the query is nearly complete. Null for alphanumeric input or
     *  anything unparseable. Cached on the canonical E.164 — the national form
     *  follows the number's own country code, so [region] only affects
     *  parsing. */
    fun nationalDigits(input: String, region: String): String? {
        if (!isLikelyPhoneNumber(input)) return null
        val e164 = toE164(input, region) ?: return null
        nationalCache[e164]?.let { return if (it == MISS) null else it }
        val out = try {
            val number = util.parse(e164, null)
            util.format(number, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
                .filter { it.isDigit() }
                .ifEmpty { null }
        } catch (_: Exception) {
            null
        }
        nationalCache[e164] = out ?: MISS
        return out
    }

    /** The national significant number of [input] as parsed in [region], with
     *  leading zeros preserved, or null when it does not parse. Used to tell
     *  whether the assumed region actually consumed the input's trunk prefix. */
    fun nationalSignificantDigits(input: String, region: String): String? {
        if (!isLikelyPhoneNumber(input)) return null
        val cleaned = input.replace(Regex("[^0-9+]"), "")
        val reg = region.uppercase()
        val key = "$cleaned|$reg"
        significantCache[key]?.let { return if (it == MISS) null else it }
        val out = try {
            util.getNationalSignificantNumber(util.parse(cleaned, reg)).ifEmpty { null }
        } catch (_: Exception) {
            null
        }
        significantCache[key] = out ?: MISS
        return out
    }

    /** True when [input] is a *valid* number for [region]. Stricter than
     *  [toE164]'s isPossibleNumber gate: used only to decide whether a national
     *  form can be attributed to a country, where a merely "possible" match
     *  (Switzerland accepts a Swedish national form as possible) is not enough. */
    fun isValidInRegion(input: String, region: String): Boolean {
        if (!isLikelyPhoneNumber(input)) return false
        val cleaned = input.replace(Regex("[^0-9+]"), "")
        val reg = region.uppercase()
        val key = "$cleaned|$reg"
        validityCache[key]?.let { return it == "1" }
        val out = try {
            val number = util.parse(cleaned, reg)
            util.isValidNumberForRegion(number, reg)
        } catch (_: Exception) {
            false
        }
        validityCache[key] = if (out) "1" else "0"
        return out
    }

    /** Longest valid country-code prefix (1-3 digits) of [digits], or "" when no
     *  prefix of it is a real country code. Used to tell a genuine country code
     *  apart from the first digits of a subscriber number. */
    fun countryCodePrefix(digits: String): String {
        if (digits.isEmpty()) return ""
        val max = minOf(3, digits.length)
        for (len in max downTo 1) {
            val code = digits.take(len).toIntOrNull() ?: continue
            val region = try { util.getRegionCodeForCountryCode(code) } catch (_: Exception) { null }
            if (!region.isNullOrBlank() && region != "ZZ") return digits.take(len)
        }
        return ""
    }

    /** ISO 3166-1 alpha-2 country code for an E.164 number, or "" when unknown.
     *  Cached: same-person checks call it per conversation. */
    fun regionFor(e164: String): String {
        regionCache[e164]?.let { return it }
        val out = try {
            val code = util.getRegionCodeForNumber(util.parse(e164, null))
            if (code == "ZZ" || code == null) "" else code
        } catch (_: Exception) {
            ""
        }
        regionCache[e164] = out
        return out
    }
}
