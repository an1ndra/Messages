package com.anindra.messages.data

/** Address identity shared by threads, contact lookup and reply gates.
 *
 *  Phone numbers are normalized to E.164. Alphanumeric sender IDs (e.g.
 *  "A1 SRB", "VM-HDFCBK") are kept verbatim: reducing them to their digits
 *  collapsed "A1 SRB" to "1", which then displayed as the sender and fused
 *  unrelated senders together (issue #207). */
object AddressIdentity {

    /** E.164 canonical spelling when [address] is a phone number, else the
     *  trimmed original so alphanumeric sender IDs survive intact.
     *
     *  A parse is only trusted when the assumed region actually consumed a
     *  leading trunk '0': if the input starts with '0' but that '0' survives
     *  into the E.164 digits, the region guess was wrong (e.g. a Swedish
     *  national number on a US device) and the address is kept verbatim instead
     *  of being rewritten into a bogus "+10..." destination. */
    fun canonical(address: String, region: String): String {
        val e164 = PhoneNumberUtils.toE164(address, region) ?: return address.trim()
        val inDigits = address.filter { it.isDigit() }
        if (inDigits.startsWith("0")) {
            // The region's plan must have consumed that leading '0'. If the
            // parsed national significant number is the whole input, the '0'
            // was not consumed — but that is only a wrong guess when the result
            // is not a valid number for the region (Italy's fixed lines keep a
            // significant leading 0, so they are valid and pass).
            if (PhoneNumberUtils.nationalSignificantDigits(address, region) == inDigits &&
                !PhoneNumberUtils.isValidInRegion(address, region)) {
                return address.trim()
            }
        }
        return e164
    }

    /** True when two spellings identify the same sender. Addresses containing
     *  a letter are alphanumeric sender IDs and must match exactly
     *  (case-insensitive); dialable numbers go through libphonenumber, and
     *  digit/punctuation-only addresses keep the issue #183 digit-run
     *  comparison as a last resort. */
    fun samePerson(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.any { it.isLetter() } || b.any { it.isLetter() }) {
            return a.trim().equals(b.trim(), ignoreCase = true)
        }
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.isEmpty() || db.isEmpty()) return false
        if (da == db) return true
        // Cheap digit-run checks first: they settle the common cases without
        // touching libphonenumber.
        val diff = da.length - db.length
        if (diff in 1..3 && da.drop(diff) == db) return true
        val rev = db.length - da.length
        if (rev in 1..3 && db.drop(rev) == da) return true
        // Only now pay for the country-plan comparison.
        return sameNumberAcrossRegions(a, b)
    }

    /** True when one spelling is E.164 and the other is a national form that
     *  yields the same E.164 when parsed in that E.164's own country.
     *
     *  libphonenumber carries each country's trunk prefix, so this needs no
     *  per-country rule (Sweden's 0, Korea's 0, India's 0, the US's none).
     *  A number that merely shares a tail cannot pass: parsing it under the
     *  other country's numbering plan either produces a different E.164 or
     *  does not parse at all. */
    private fun sameNumberAcrossRegions(a: String, b: String): Boolean {
        val ea = e164Of(a)
        val eb = e164Of(b)
        if (ea != null && eb != null) return ea == eb
        if (ea != null) {
            val region = PhoneNumberUtils.regionFor(ea)
            return region.isNotEmpty() &&
                PhoneNumberUtils.isValidInRegion(b, region) &&
                PhoneNumberUtils.toE164(b, region) == ea
        }
        if (eb != null) {
            val region = PhoneNumberUtils.regionFor(eb)
            return region.isNotEmpty() &&
                PhoneNumberUtils.isValidInRegion(a, region) &&
                PhoneNumberUtils.toE164(a, region) == eb
        }
        return false
    }

    /** The E.164 spelling of [address] under the device region, or null when the
     *  region cannot be trusted for it (see [canonical]'s trunk-zero guard) or
     *  the address is not a parseable number. */
    private fun e164Of(address: String): String? {
        val normalized = canonical(address, PhoneNumberUtils.region())
        if (!normalized.startsWith("+")) return null
        // Re-validate: an unparseable "+..." string was kept verbatim above.
        return PhoneNumberUtils.toE164(normalized, "ZZ")
    }

    /** True for dialable phone/short-code addresses; false for alphanumeric
     *  sender IDs, which cannot receive replies. */
    fun isReplyable(address: String): Boolean = PhoneNumberUtils.isDialableAddress(address)

    /** True when [query] can only be a phone number: at least one digit, and
     *  nothing but digits and the punctuation people type numbers with. This is
     *  what splits number search from text search — a number identifies a
     *  conversation, never a message (issue #284). */
    fun isNumberQuery(query: String): Boolean =
        query.any { it.isDigit() } && query.all { it.isDigit() || it in "+-(). " }

    /** True when [query] is a number too short to match anything yet, so the
     *  list should stay unfiltered while the user is still typing it. */
    fun tooShortToBeNumber(query: String): Boolean =
        isNumberQuery(query) && query.count { it.isDigit() } < MIN_SEARCH_DIGITS

    /** True when [query] is a phone-number fragment that identifies [address],
     *  however each side was formatted (spaces, dashes, `+`, country code).
     *  The conversation search needs this because the UI shows/accepts
     *  `555-000-1234` while the address is stored as `+15550001234`.
     *
     *  Containment, not suffix, because the query is usually partial: a suffix
     *  test cannot succeed until nearly the whole number is typed, which made a
     *  contact appear only at the last digit.
     *
     *  An address containing a letter is an alphanumeric sender ID, never a
     *  number, and is rejected outright. Stripping one to its digits is what made
     *  `X1 INFO` match every query ending in 1 — it reduces to "1", so
     *  `q.endsWith(a)` flickered on and off as the user typed. Same rule as
     *  [samePerson] above: a letter means it is not dialable.
     *
     *  [ContactLookup.compareDigits] stays as the last resort for spellings the
     *  three digit runs below miss — the same last-seven rule contact lookup
     *  already uses, rather than a second copy of it here. */
    fun matchesNumber(address: String, query: String): Boolean {
        if (address.any { it.isLetter() }) return false
        val q = query.filter { it.isDigit() }
        if (q.length < MIN_SEARCH_DIGITS) return false
        val a = address.filter { it.isDigit() }
        if (a.isEmpty()) return false
        if (a.contains(q)) return true
        nationalSpellings(a).forEach { if (it.contains(q)) return true }
        // Reverse direction, only once the address carries enough digits to be a
        // real number rather than something reduced below significance.
        if (a.length >= ContactLookup.MIN_MATCH_DIGITS && q.endsWith(a)) return true
        return ContactLookup.compareDigits(a, q)
    }

    /** [e164Digits] as national digits, plus that run with the trunk zero
     *  stripped: "+447700900123" → ["07700900123", "7700900123"]. Covers both
     *  the leading-zero national form (Italy, Germany) and the plain one. The
     *  region is irrelevant — the input is already E.164 — so "ZZ" is passed to
     *  keep the parse off the device region. */
    private fun nationalSpellings(e164Digits: String): List<String> {
        val national = PhoneNumberUtils.nationalDigits("+$e164Digits", "ZZ")
            ?: return emptyList()
        val withoutZero = national.trimStart('0')
        return if (withoutZero.isEmpty() || withoutZero == national) listOf(national)
        else listOf(national, withoutZero)
    }

    /** A query with fewer digits than this is treated as text, not a number, so
     *  a stray "1" does not match every conversation. */
    private const val MIN_SEARCH_DIGITS = 3
}
