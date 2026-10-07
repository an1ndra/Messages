package com.anindra.messages.data

import com.anindra.messages.hideUrls

/**
 * Matching rules for handing a home-list search query to the chat screen, so
 * the chat can highlight the hit and scroll to it. Kept pure so the ranges the
 * bubble styles and the message the list scrolls to are unit tested rather
 * than re-derived in two places.
 */
object MessageSearch {

    /** Case-insensitive containment: the same rule the home list applies to a
     *  conversation snippet. */
    fun matches(body: String, query: String): Boolean =
        query.isNotBlank() && body.contains(query, ignoreCase = true)

    /** A query may only match what the user can see. A locked message's body
     *  is masked everywhere and must never surface as a hit, and with Hide
     *  links on the redacted body is the visible one — the same redaction the
     *  list and the chat paint, so a search cannot reveal hidden content.
     *  SQL callers keep a LIKE prefilter only; this is the rule. */
    fun matchesVisible(
        body: String,
        query: String,
        locked: Boolean,
        hideLinks: Boolean,
        redactor: (String) -> String = ::hideUrls
    ): Boolean = !locked && matches(if (hideLinks) redactor(body) else body, query)

    /**
     * The newest message in [messages] whose body matches [query], or null.
     * The list is oldest-first, so the newest hit is the last one — that is the
     * message a snippet match is most likely to be.
     */
    fun focusedId(messages: List<Message>, query: String): Long? =
        if (query.isBlank()) null
        else messages.lastOrNull { matches(it.body, query) }?.id

    /**
     * Every occurrence of [query] in [body], case-insensitively, as inclusive
     * ranges the bubble can style. Matching without folding the string keeps
     * the offsets valid for characters whose lowercase form changes length.
     */
    fun ranges(body: String, query: String): List<IntRange> {
        if (query.isBlank()) return emptyList()
        val out = mutableListOf<IntRange>()
        var start = 0
        while (true) {
            val at = body.indexOf(query, start, ignoreCase = true)
            if (at < 0) break
            out += at until at + query.length
            start = at + query.length
        }
        return out
    }

    /**
     * Clamped step through [count] matches for the next/previous buttons.
     * Returns -1 when there is nothing to move through, so a caller can keep a
     * single "no match" index without branching.
     */
    fun step(index: Int, delta: Int, count: Int): Int =
        if (count <= 0) -1 else (index + delta).coerceIn(0, count - 1)
}
