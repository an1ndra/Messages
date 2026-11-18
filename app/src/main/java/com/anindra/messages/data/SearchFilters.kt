package com.anindra.messages.data

/**
 * The operators a search query can carry, with everything the operators did not
 * consume kept as plain text so an unrecognised query behaves exactly as it
 * always has.
 *
 * `from:` narrows by who the conversation is with, `has:attachment` to threads
 * carrying a media part, and `before:`/`after:` to a period — all combinable,
 * all optional. A value may be quoted to span spaces (`from:"Kami 0910"`), and
 * a date is the calendar's own numeric year/month/day, so a Persian user types
 * the date the way the rest of the app shows it to them.
 *
 * The parser is pure: a date stays a [CalendarDayInput] until the resolved
 * calendar converts it on the UI side, where ICU lives and can be faked in
 * tests. An invalid date or an unknown operator is not an error — the token
 * simply remains part of [SearchFilters.plainText].
 */
object SearchFilters {

    /** The parsed result, plus where each token came from so a chip can remove
     *  exactly what produced it. */
    class Parsed internal constructor(
        val filters: SearchFiltersData,
        internal val spans: List<Span>
    ) {
        /** The query with the first token of [kind] removed, whitespace closed
         *  up. [original] when no such token is found. */
        fun without(kind: FilterKind): String {
            val span = spans.firstOrNull { it.kind == kind } ?: return original
            val out = StringBuilder(original.length)
            for (s in spans) {
                if (s === span) continue
                out.append(original.substring(s.range))
            }
            return out.toString().trim().replace(Regex("\\s{2,}"), " ")
        }

        internal var original: String = ""
    }

    /** One parsed token: an operator [kind], or null for plain text. */
    internal data class Span(val kind: FilterKind?, val range: IntRange)

    enum class FilterKind { FROM, ATTACHMENTS, BEFORE, AFTER }

    data class SearchFiltersData(
        val from: String? = null,
        val hasAttachment: Boolean = false,
        val before: CalendarDayInput? = null,
        val after: CalendarDayInput? = null,
        val plainText: String = ""
    ) {
        val hasMessageFilters: Boolean
            get() = hasAttachment || before != null || after != null
    }

    fun parse(query: String): Parsed {
        val spans = ArrayList<Span>()
        var from: String? = null
        var hasAttachment = false
        var before: CalendarDayInput? = null
        var after: CalendarDayInput? = null
        var i = 0
        while (i < query.length) {
            if (query[i] == ' ') {
                spans += Span(null, i..i)
                i++
                continue
            }
            val lower = query.substring(i).lowercase()
            when {
                lower.startsWith("from:") -> {
                    val value = readValue(query, i + 5)
                    if (value == null) {
                        // An operator without a value is not one: `from: Dad`
                        // stays plain text, the way an unknown word does.
                        val end = query.indexOf(' ', i).takeIf { it >= 0 } ?: query.length
                        spans += Span(null, i until end)
                        i = end
                    } else {
                        from = value.first
                        spans += Span(FilterKind.FROM, i until value.second)
                        i = value.second
                    }
                }
                lower.startsWith("before:") -> {
                    val value = readValue(query, i + 7)
                    val day = value?.let { parseDay(it.first) }
                    if (day == null) {
                        val end = query.indexOf(' ', i).takeIf { it >= 0 } ?: query.length
                        spans += Span(null, i until end)
                        i = end
                    } else {
                        before = day
                        spans += Span(FilterKind.BEFORE, i until value!!.second)
                        i = value.second
                    }
                }
                lower.startsWith("after:") -> {
                    val value = readValue(query, i + 6)
                    val day = value?.let { parseDay(it.first) }
                    if (day == null) {
                        val end = query.indexOf(' ', i).takeIf { it >= 0 } ?: query.length
                        spans += Span(null, i until end)
                        i = end
                    } else {
                        after = day
                        spans += Span(FilterKind.AFTER, i until value!!.second)
                        i = value.second
                    }
                }
                lower.startsWith("has:attachment") &&
                    (i + 14 == query.length || query[i + 14] == ' ') -> {
                    hasAttachment = true
                    spans += Span(FilterKind.ATTACHMENTS, i until i + 14)
                    i += 14
                }
                else -> {
                    val end = query.indexOf(' ', i).takeIf { it >= 0 } ?: query.length
                    spans += Span(null, i until end)
                    i = end
                }
            }
        }
        val plain = spans
            .filter { it.kind == null }
            .joinToString("") { query.substring(it.range) }
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return Parsed(
            SearchFiltersData(from, hasAttachment, before, after, plain),
            spans
        ).also { it.original = query }
    }

    /** A value starting at [start]: a quoted phrase or one whitespace-free
     *  token. The end index points at the separator, never past the end. */
    private fun readValue(query: String, start: Int): Pair<String, Int>? {
        if (start >= query.length || query[start] == ' ') return null
        if (query[start] == '"') {
            val close = query.indexOf('"', start + 1)
            if (close < 0) return query.substring(start + 1) to query.length
            return query.substring(start + 1, close) to (close + 1)
        }
        val end = query.indexOf(' ', start).takeIf { it >= 0 } ?: query.length
        return query.substring(start, end) to end
    }

    /**
     * A typed date: the calendar's own numeric year/month/day, digits in any
     * script an SMS uses. Null when the value is not a date, which leaves the
     * token as plain text. Day-level calendar validation (a 31st of a 30-day
     * month) is the converter's business, not the parser's.
     */
    fun parseDay(text: String): CalendarDayInput? {
        val ascii = StringBuilder(text.length)
        for (c in text) {
            when {
                c in '\u06F0'..'\u06F9' -> ascii.append('0' + (c - '\u06F0'))
                c in '\u0660'..'\u0669' -> ascii.append('0' + (c - '\u0660'))
                else -> ascii.append(c)
            }
        }
        val m = Regex("""(\d{1,4})[/-](\d{1,2})[/-](\d{1,2})""").matchEntire(ascii.toString())
            ?: return null
        val year = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        val day = m.groupValues[3].toInt()
        if (year !in 1..9999 || month !in 1..12 || day !in 1..31) return null
        return CalendarDayInput(year, month, day)
    }
}

/** A (year, month, day) as typed, not yet resolved against a calendar. */
data class CalendarDayInput(val year: Int, val month1: Int, val day: Int)

/**
 * The message-level half of a parsed query, with dates already converted to
 * instant bounds. `before` is inclusive through the end of that day; `after`
 * from the start of it. The conversation-level `from:` filter needs no SQL —
 * it is applied where conversations are filtered.
 */
data class MessageSearchFilters(
    val hasAttachment: Boolean = false,
    val beforeEndMillis: Long? = null,
    val afterStartMillis: Long? = null
) {
    val isPresent: Boolean
        get() = hasAttachment || beforeEndMillis != null || afterStartMillis != null
}
