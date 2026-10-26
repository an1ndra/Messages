package com.anindra.messages.ui

/** Render-layer bidi helpers. Inside RTL text (Persian, Arabic, Hebrew) a
 *  number written with separators — or with a leading '+'/'(' — is split into
 *  several directional runs, and Unicode places those runs right-to-left, so
 *  e.g. "+98 999 862 0453" paints as "0453 862 999 98+". Wrapping the run in
 *  left-to-right isolates (U+2066 … U+2069) pins it to LTR order. Purely a
 *  display transform: nothing is persisted. */
object BidiText {
    const val LRI = '\u2066'
    const val PDI = '\u2069'

    private const val NBSP = '\u00A0'

    /** Base direction a message is laid out in. */
    enum class Direction { LTR, RTL }

    /** Digits of every script an SMS actually uses: ASCII, Arabic-Indic
     *  (U+0660..0669) and Extended Arabic-Indic, which is what Persian bank
     *  messages are written in. Java's `\d` is ASCII-only, so a Persian amount
     *  was never recognised as a number and a word like "مانده" could be
     *  reordered into the middle of it (#345). */
    private const val DIGITS = "0-9\u0660-\u0669\u06F0-\u06F9"

    /** A run that starts and ends with a digit, with an optional leading
     *  '-'/'+'/'(' and trailing ')', and any run of digits/separators between.
     *  The Arabic thousands (U+066C) and decimal (U+066B) separators count as
     *  separators, as does the Arabic comma, which some banks use as a
     *  thousands mark.
     *
     *  ':' and '_' are separators too (#345). Without ':' a Mellat stamp like
     *  "1405/07/30 - 16:59" matched only as far as the hour, leaving ":59"
     *  outside the isolate to be reordered — which is why the hour and minute
     *  ended up on opposite sides of the date. '_' is the same bug for
     *  Pasargad ("07/30_04:00").
     *
     *  'x'/'X' and '*' belong in a run as well: a masked card number
     *  ("5022-29xx-xxxx-9227") is digits around Latin letters, whose strong
     *  LTR direction used to split the number into runs that Unicode then
     *  reordered — the sender's own card digits came out jumbled, while the
     *  same text read correctly once selected (#345 follow-up).
     *
     *  Whitespace is horizontal only: a line break ends a run, so one message's
     *  amount is never isolated together with the next line's timestamp. */
    private val NUMBER_RUN = Regex("""[-+]?\(?[$DIGITS][$DIGITS \t()./$NBSP,_:\u060C\u066B\u066CxX*-]*[$DIGITS]\)?""")

    /** A link, in the two shapes an SMS actually carries: with a scheme or
     *  "www.", or bare like "11r.eu/t" and "l.tpshp.co/3lDXoA". Latin letters
     *  inside a link are strong, so without this a Persian message that ends
     *  on a link was laid out LTR by its own link (#362 follow-up).
     *
     *  This is the direction-side counterpart of `Linkify.WEB_URLS`, which
     *  stays the authority on what is *tappable*; a bare host needs a dot and a
     *  two-letter TLD so abbreviations like "e.g" do not qualify. */
    private val LINK_RUN = Regex(
        "(?i)(?:[a-z][a-z0-9+.\\-]*://|www\\.)\\S*" +
            "|@?(?:[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?\\.)+[a-z]{2,}(?:/\\S*)?"
    )

    private fun needsIsolation(run: String): Boolean = run.any { !it.isDigit() }

    /** Ranges of [text] that are links — see [LINK_RUN]. */
    fun linkRanges(text: String): List<IntRange> = LINK_RUN.findAll(text).map { it.range }.toList()

    /** A strong direction, or null for everything that is weak — digits (Arab
     *  and Latin alike), spaces, punctuation, emoji. Only letters vote.
     *
     *  Two traps live in that rule (#362 follow-up): emoji are a surrogate
     *  pair, and `Character.getDirectionality(char)` reports a *lone surrogate*
     *  as LTR, so a Persian message ending in 📣 was laid out LTR by its own
     *  emoji; and flags are two regional indicators, which Unicode genuinely
     *  classes L. Both are symbols, so requiring a letter drops them without a
     *  hand-maintained emoji table. Callers must pass a whole code point. */
    private fun strongDirection(codePoint: Int): Direction? {
        if (!Character.isLetter(codePoint)) return null
        return when (Character.getDirectionality(codePoint)) {
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> Direction.LTR
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> Direction.RTL
            else -> null
        }
    }

    /** Unicode P2: the paragraph direction is set by its first strong
     *  character. No strong character (a bare number) counts as LTR. This is
     *  what the composer follows — it must not flip under the cursor while the
     *  user is still typing the first word (#362). */
    fun firstStrongDirection(text: String): Direction {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            strongDirection(cp)?.let { return it }
            i += Character.charCount(cp)
        }
        return Direction.LTR
    }

    /** The direction a whole message is laid out in (#362, matching Google
     *  Messages): the first and last strong characters decide. Ignoring
     *  punctuation and digits means a trailing "!" or a phone number never
     *  votes. When the two ends disagree the last one wins, so a message that
     *  ends in Persian is laid out as Persian even if it opens in English.
     *
     *  Links do not vote either (#362 follow-up): "…\nl.tpshp.co/3lDXoA" is a
     *  Persian broadcast whose last Latin letter lives inside the link, and
     *  letting that letter decide flipped the whole message out of RTL. */
    fun direction(text: String): Direction {
        val links = linkRanges(text)
        var first: Direction? = null
        var last: Direction? = null
        var l = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            while (l < links.size && links[l].last < i) l++
            if (!(l < links.size && links[l].first <= i)) {
                val d = strongDirection(cp)
                if (d != null) {
                    if (first == null) first = d
                    last = d
                }
            }
            i += Character.charCount(cp)
        }
        val head = first ?: return Direction.LTR
        return if (head == last) head else last!!
    }

    /** Lines of [text] with no strong character in them once links are
     *  ignored: a bare amount, a bare link, or "@tabdeal.org #85904". Unicode
     *  keeps such a line in the paragraph's direction, which reorders it
     *  against its own reading order and pins a lone URL to the right edge of
     *  a Persian bubble (#362 follow-up). */
    private fun ltrOnlyLines(text: String, links: List<IntRange>): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = 0
        while (start <= text.length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline
            if (end > start) {
                var l = 0
                var i = start
                var strong = false
                while (i < end) {
                    val cp = text.codePointAt(i)
                    while (l < links.size && links[l].last < i) l++
                    if (!(l < links.size && links[l].first <= i) && strongDirection(cp) != null) {
                        strong = true
                        break
                    }
                    i += Character.charCount(cp)
                }
                if (!strong) out += start..(end - 1)
            }
            if (newline < 0) break
            start = newline + 1
        }
        return out
    }

    /** [value] as a single LTR run; idempotent. */
    fun ltr(value: String): String {
        if (value.isEmpty() || (value.first() == LRI && value.last() == PDI)) return value
        return "$LRI$value$PDI"
    }

    /** Isolated [text] plus a boundary map enabling [remap] of styled ranges. */
    class Isolated internal constructor(
        val text: String,
        private val offsetMap: IntArray,
        /** The base direction [text] should be painted in. */
        val direction: Direction,
        /** How many lines were pinned LTR by the transform. */
        val ltrLines: Int
    ) {
        /** Maps an inclusive range in the original string to its inclusive
         *  counterpart in [text], absorbing any isolates inserted at its edges. */
        fun remap(range: IntRange): IntRange =
            offsetMap[range.first]..(offsetMap[range.last + 1] - 1)
    }

    /** Isolates every number-like run in [text]. Runs intersecting [excluded]
     *  (e.g. digits inside a link) are left untouched, and so are runs on a
     *  line that is pinned LTR as a whole. No-op for LTR text: a single LTR
     *  run already keeps its order, and splitting it into several isolated
     *  runs would reorder them in an RTL layout.
     *
     *  Decided by [direction], the same base direction the bubble renders in,
     *  so a message cannot be isolated as if it were RTL and then painted LTR
     *  (or the other way round). */
    fun isolateNumberRuns(text: String, excluded: List<IntRange> = emptyList()): Isolated {
        val direction = direction(text)
        val identity = Isolated(text, IntArray(text.length + 1) { it }, direction, 0)
        if (direction != Direction.RTL) return identity
        val lines = ltrOnlyLines(text, linkRanges(text))
        val runs = NUMBER_RUN.findAll(text)
            .map { it.range }
            .filter { needsIsolation(text.substring(it)) }
            .filter { r -> excluded.none { e -> r.first <= e.last && e.first <= r.last } }
            .filter { r -> lines.none { l -> r.first >= l.first && r.last <= l.last } }
            .toList()
        if (runs.isEmpty() && lines.isEmpty()) return identity

        val insert = HashMap<Int, MutableList<Char>>()
        for (r in runs) {
            insert.getOrPut(r.first) { ArrayList(1) }.add(LRI)
            insert.getOrPut(r.last + 1) { ArrayList(1) }.add(PDI)
        }
        for (l in lines) {
            insert.getOrPut(l.first) { ArrayList(1) }.add(LRI)
            insert.getOrPut(l.last + 1) { ArrayList(1) }.add(PDI)
        }

        val offsetMap = IntArray(text.length + 1)
        val sb = StringBuilder(text.length + insert.size * 2)
        var n = 0
        for (o in 0..text.length) {
            offsetMap[o] = n
            var closing = false
            insert[o]?.forEach { m ->
                sb.append(m); n++
                if (m == PDI) closing = true
            }
            if (closing) offsetMap[o] = n
            if (o < text.length) {
                sb.append(text[o]); n++
            }
        }
        return Isolated(sb.toString(), offsetMap, direction, lines.size)
    }
}
