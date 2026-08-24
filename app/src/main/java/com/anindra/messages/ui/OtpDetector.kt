package com.anindra.messages.ui

object OtpDetector {

    private val ANY_KEYWORD = Regex(
        """(?i)\b(otp|one[\s-]?time|verification|verify|passcode|password|security\s?code|2fa|pin|code|token)\b"""
    )
    private val STRONG_KEYWORD = Regex(
        """(?i)\b(otp|one[\s-]?time|verification|verify|passcode|security\s?code|2fa)\b"""
    )
    private val KEYWORD_BEFORE = Regex(
        """(?i)\b(?:otp|code|pin|passcode|password|verification|verify|token|2fa)\b\D{0,10}(\d{4,8})(?!\d)"""
    )
    private val KEYWORD_AFTER = Regex(
        """(?i)(?<!\d)(\d{4,8})(?:\s*(?:is|:))?\s+(?:(?:your|the|my|google|app|login|bank)\s+){0,3}(?:verification\s+code|security\s+code|otp|passcode|password|code|pin)\b"""
    )
    private val GROUPED_6 = Regex("""(?<![\d-])(\d{3})[ -](\d{3})(?![\d-])""")
    private val GROUPED_8 = Regex("""(?<![\d-])(\d{4})[ -](\d{4})(?![\d-])""")
    private val BARE_6 = Regex("""(?<!\d)(\d{6})(?!\d)""")
    private val YEAR = Regex("""(19|20)\d{2}""")
    private val KEYWORD_DIRECT = Regex(
        """(?i)(?:otp|code|pin|passcode|password|verification|verify|token|2fa)\W{0,3}$"""
    )
    private val MONEY_BEFORE = Regex("""(?:₹|€|£|\$|Rs\.?|INR)\P{L}*$""", RegexOption.IGNORE_CASE)

    fun findRanges(body: String): List<IntRange> {
        val found = mutableListOf<IntRange>()

        fun keep(range: IntRange): Boolean {
            val digits = body.substring(range)
            if (digits.length == 4 && YEAR.matches(digits) &&
                !KEYWORD_DIRECT.containsMatchIn(body.substring(0, range.first))
            ) return false
            if (MONEY_BEFORE.containsMatchIn(body.substring(0, range.first).takeLast(6))) return false
            return found.none { it.first <= range.last && range.first <= it.last }
        }

        if (ANY_KEYWORD.containsMatchIn(body)) {
            for (regex in listOf(GROUPED_6, GROUPED_8)) {
                for (m in regex.findAll(body)) {
                    if (keep(m.range)) found += m.range
                }
            }
        }
        for (regex in listOf(KEYWORD_BEFORE, KEYWORD_AFTER)) {
            for (m in regex.findAll(body)) {
                val g = m.groups.last()!!.range
                if (keep(g)) found += g
            }
        }
        if (found.isEmpty() && STRONG_KEYWORD.containsMatchIn(body)) {
            for (m in BARE_6.findAll(body)) {
                if (keep(m.range)) found += m.range
            }
        }
        return found.sortedBy { it.first }
    }
}
