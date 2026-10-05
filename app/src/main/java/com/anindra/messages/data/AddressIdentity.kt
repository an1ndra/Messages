package com.anindra.messages.data

/** Address identity shared by threads, contact lookup and reply gates.
 *
 *  Phone numbers are normalized to E.164. Alphanumeric sender IDs (e.g.
 *  "A1 SRB", "VM-HDFCBK") are kept verbatim: reducing them to their digits
 *  collapsed "A1 SRB" to "1", which then displayed as the sender and fused
 *  unrelated senders together (issue #207). */
object AddressIdentity {

    /** E.164 canonical spelling when [address] is a phone number, else the
     *  trimmed original so alphanumeric sender IDs survive intact. */
    fun canonical(address: String, region: String): String =
        PhoneNumberUtils.toE164(address, region) ?: address.trim()

    /** True when two spellings identify the same sender. Addresses containing
     *  a letter are alphanumeric sender IDs and must match exactly
     *  (case-insensitive); digit/punctuation-only addresses keep the issue
     *  #183 digit-run comparison. */
    fun samePerson(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.any { it.isLetter() } || b.any { it.isLetter() }) {
            return a.trim().equals(b.trim(), ignoreCase = true)
        }
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.isEmpty() || db.isEmpty()) return false
        if (da == db) return true
        val diff = da.length - db.length
        if (diff in 1..3 && da.drop(diff) == db) return true
        val rev = db.length - da.length
        return rev in 1..3 && db.drop(rev) == da
    }

    /** True for dialable phone/short-code addresses; false for alphanumeric
     *  sender IDs, which cannot receive replies. */
    fun isReplyable(address: String): Boolean = PhoneNumberUtils.isLikelyPhoneNumber(address)

    /** True when [query] is a phone-number fragment that identifies [address],
     *  however each side was formatted (spaces, dashes, `+`, country code).
     *  The conversation search needs this because the UI shows/accepts
     *  `555-000-1234` while the address is stored as `+15550001234`. */
    fun matchesNumber(address: String, query: String): Boolean {
        val q = query.filter { it.isDigit() }
        if (q.length < MIN_SEARCH_DIGITS) return false
        val a = address.filter { it.isDigit() }
        return a.isNotEmpty() && (a.endsWith(q) || q.endsWith(a))
    }

    /** A query with fewer digits than this is treated as text, not a number, so
     *  a stray "1" does not match every conversation. */
    private const val MIN_SEARCH_DIGITS = 3
}
