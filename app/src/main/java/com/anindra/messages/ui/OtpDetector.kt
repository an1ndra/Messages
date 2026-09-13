package com.anindra.messages.ui

object OtpDetector {

    // ── Tier 1: STRONG keywords (high precision, low false-positives) ──
    private val STRONG_KEYWORD = Regex(
        """(?i)\b(otp|one[\s-]?time|verification|passcode|2fa|security[\s-]?code)\b"""
    )

    // ── Tier 2: WEAK keywords (need tighter context before trusting) ──
    private val WEAK_KEYWORD = Regex(
        """(?i)\b(code|pin|token|password)\b"""
    )

    // ── Alphanumeric OTP: mix of upper/lower digits, 4-8 chars ──
    private val ALPHA_NUM_OTP = Regex(
        """(?<![a-zA-Z\d])([a-zA-Z]\d[a-zA-Z0-9]{2,6}[a-zA-Z0-9]?)(?![a-zA-Z\d])"""
    )

    // ── Digit patterns ──
    private val GROUPED_6 = Regex("""(?<![\d-])(\d{3})[ -](\d{3})(?![\d-])""")
    private val GROUPED_8 = Regex("""(?<![\d-])(\d{4})[ -](\d{4})(?![\d-])""")
    private val BARE_6 = Regex("""(?<!\d)(\d{6})(?!\d)""")
    private val BARE_4_8 = Regex("""(?<!\d)(\d{4,8})(?!\d)""")
    private val YEAR = Regex("""(19|20)\d{2}""")

    // ── Filter: exclude money prefixes ──
    private val MONEY_PREFIX = Regex("""(?:₹|€|£|\$|Rs\.?|INR)\P{L}*""", RegexOption.IGNORE_CASE)

    // ── Key: keyword → distance to nearby digit group ──
    private data class Candidate(val range: IntRange, val distance: Int)

    fun findRanges(body: String): List<IntRange> {
        val candidates = mutableListOf<Candidate>()

        // ── 1. STRONG keyword + digit window (tight) ──
        _strongKeywordBefore(body, candidates)
        _strongKeywordAfter(body, candidates)
        _strongKeywordGrouped(body, candidates)

        // ── 2. WEAK keyword requires co-occurrence with STRONG or tighter window ──
        _weakKeywordTight(body, candidates)

        // ── 3. Fallback: strong keyword + bare 6-digit ──
        _strongFallbackBare6(body, candidates)

        // ── 4. Fallback: alphanumeric OTP (only with strong keyword) ──
        _alphanumericFallback(body, candidates)

        // ── Deduplicate + pick closest to any keyword ──
        return _resolveCandidates(body, candidates)
    }

    // ── Strong: "Your otp 1234" ──
    private fun _strongKeywordBefore(body: String, out: MutableList<Candidate>) {
        val pat = Regex("""(?i)\b(otp|one[\s-]?time|verification|passcode|2fa|security[\s-]?code)\b\D{1,8}(\d{4,8})(?!\d)""")
        for (m in pat.findAll(body)) {
            val dist = m.groups[2]!!.range.first - m.range.first
            out += Candidate(m.groups[2]!!.range, dist)
        }
    }

    // ── Strong: "1234 is your verification code" ──
    private fun _strongKeywordAfter(body: String, out: MutableList<Candidate>) {
        val pat = Regex(
            """(?<!\d)(\d{4,8})(?:\s*(?:is|:))?\s+(?:(?:your|the|my|google|app|login|bank)\s+){0,3}(?:verification[\s-]?code|one[\s-]?time[\s-]?code|otp|passcode|2fa|security[\s-]?code)\b"""
        )
        for (m in pat.findAll(body)) {
            val dist = m.range.last - m.groups[1]!!.range.last
            out += Candidate(m.groups[1]!!.range, dist)
        }
    }

    // ── Strong: any keyword present → also scan grouped digits ──
    private fun _strongKeywordGrouped(body: String, out: MutableList<Candidate>) {
        if (!STRONG_KEYWORD.containsMatchIn(body)) return
        for (regex in listOf(GROUPED_6, GROUPED_8)) {
            for (m in regex.findAll(body)) {
                out += Candidate(m.range, 999) // grouped = loose proximity
            }
        }
    }

    // ── Weak keyword: require STRONG keyword co-occurrence OR tight window (3 chars) ──
    private fun _weakKeywordTight(body: String, out: MutableList<Candidate>) {
        if (!WEAK_KEYWORD.containsMatchIn(body)) return
        val hasStrong = STRONG_KEYWORD.containsMatchIn(body)
        // Tight window: keyword within 3 chars of digits
        val tightPat = Regex("""(?i)\b(code|pin|token|password)\b\D{1,3}(\d{4,8})(?!\d)""")
        val tightMatches = tightPat.findAll(body).toList()
        for (m in tightMatches) {
            val dist = m.groups[2]!!.range.first - m.range.first
            out += Candidate(m.groups[2]!!.range, dist)
        }
        // If no tight match found but strong keyword exists, also accept weak matches with 5-char window
        if (hasStrong && tightMatches.isEmpty()) {
            val loosePat = Regex("""(?i)\b(code|pin|token|password)\b\D{1,5}(\d{4,8})(?!\d)""")
            for (m in loosePat.findAll(body)) {
                out += Candidate(m.groups[2]!!.range, 5)
            }
        }
    }

    // ── Fallback: strong keyword + bare 6 digits ──
    private fun _strongFallbackBare6(body: String, out: MutableList<Candidate>) {
        if (out.isNotEmpty()) return
        if (!STRONG_KEYWORD.containsMatchIn(body)) return
        for (m in BARE_6.findAll(body)) {
            out += Candidate(m.range, 999)
        }
    }

    // ── Fallback: alphanumeric OTP with strong keyword ──
    private fun _alphanumericFallback(body: String, out: MutableList<Candidate>) {
        if (out.isNotEmpty()) return
        if (!STRONG_KEYWORD.containsMatchIn(body)) return
        for (m in ALPHA_NUM_OTP.findAll(body)) {
            out += Candidate(m.range, 999)
        }
    }

    // ── Resolve: deduplicate, filter false-positives, pick closest to keyword ──
    private fun _resolveCandidates(body: String, raw: List<Candidate>): List<IntRange> {
        if (raw.isEmpty()) return emptyList()

        // Sort by distance (closest to keyword first), then by position
        val sorted = raw.sortedBy { it.distance }.distinctBy { it.range }

        return sorted.filter { c ->
            val digits = body.substring(c.range)
            // Exclude years (19xx/20xx) unless keyword nearby
            if (digits.length == 4 && YEAR.matches(digits)) {
                val nearbyKeyword = body.substring(0, c.range.first).takeLast(20).let {
                    it.contains(Regex("""(?i)(otp|code|pin|verification|passcode|2fa)"""))
                }
                if (!nearbyKeyword) return@filter false
            }
            // Exclude money patterns
            val beforeRange = maxOf(0, c.range.first - 6)
            if (MONEY_PREFIX.containsMatchIn(body.substring(beforeRange, c.range.first))) return@filter false
            true
        }.map { it.range }
    }
}
