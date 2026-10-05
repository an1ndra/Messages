package com.anindra.messages.data

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
