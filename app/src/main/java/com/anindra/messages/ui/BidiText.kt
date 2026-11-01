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
    private val NUMBER_RUN =
        Regex("""[-+]?\(?[$DIGITS][$DIGITS \t()./$NBSP,_:\u060C\u066B\u066CxX*-]*[$DIGITS]\)?\**""")

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

    /** Unicode P2: the first strong character decides. Links never vote
     *  (#362 follow-up) — a Persian broadcast that ends on "l.tpshp.co/…" is
     *  still Persian — and neither do numbers, punctuation or emoji, so "123
     *  سلام" is RTL and a bare number or URL is LTR.
     *
     *  Applied per line, because that is what Google Messages and Fossify both
     *  do: each paragraph (line) is laid out in the direction its own first
     *  strong character gives, and aligned accordingly, so "18.06" and "11:24
     *  PM" sit at the left of an otherwise Persian bubble (#362). */
    fun firstStrongDirection(text: String): Direction {
        val links = linkRanges(text)
        var l = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            while (l < links.size && links[l].last < i) l++
            if (!(l < links.size && links[l].first <= i)) strongDirection(cp)?.let { return it }
            i += Character.charCount(cp)
        }
        return Direction.LTR
    }

    /** A line of [text] and the direction it is laid out in. */
    class Line internal constructor(val range: IntRange, val direction: Direction)

    /** Every non-empty line of [text] with its own base direction. */
    fun lines(text: String): List<Line> {
        val links = linkRanges(text)
        val out = ArrayList<Line>()
        var start = 0
        while (start <= text.length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline
            if (end > start) out += Line(start..(end - 1), firstStrongDirection(text, start, end, links))
            if (newline < 0) break
            start = newline + 1
        }
        return out
    }

    private fun firstStrongDirection(
        text: String,
        start: Int,
        end: Int,
        links: List<IntRange>
    ): Direction {
        var l = 0
        var i = start
        while (i < end) {
            val cp = text.codePointAt(i)
            while (l < links.size && links[l].last < i) l++
            if (!(l < links.size && links[l].first <= i)) strongDirection(cp)?.let { return it }
            i += Character.charCount(cp)
        }
        return Direction.LTR
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
        /** The base direction of each line of [text], in order. */
        val lines: List<Direction>
    ) {
        /** Maps an inclusive range in the original string to its inclusive
         *  counterpart in [text], absorbing any isolates inserted at its edges. */
        fun remap(range: IntRange): IntRange =
            offsetMap[range.first]..(offsetMap[range.last + 1] - 1)

        /** How many lines are painted right-to-left. */
        val rtlLines: Int get() = lines.count { it == Direction.RTL }
    }

    /** Isolates every number-like run on a line that is laid out RTL, so
     *  "0999 862 0453" or a masked card number keeps its order instead of
     *  being split by the spaces around it, and pins every LTR line as one run
     *  so a card mask or a bank timestamp cannot be reordered by the line next
     *  to it. Runs intersecting [excluded] (e.g. digits inside a link) are left
     *  alone, and a number on an LTR line needs no isolate — the line already
     *  reads left to right.
     *
     *  The line directions here are the rule the platform itself applies to the
     *  painted text (`TextDirection.Content`), so the transform can never
     *  isolate as if a line were RTL while it is painted LTR. */
    fun isolateNumberRuns(text: String, excluded: List<IntRange> = emptyList()): Isolated {
        val lines = lines(text)
        val identity = Isolated(text, IntArray(text.length + 1) { it }, lines.map { it.direction })
        val runs = NUMBER_RUN.findAll(text)
            .map { it.range }
            .filter { r -> lines.firstOrNull { r.first in it.range }?.direction == Direction.RTL }
            .filter { needsIsolation(text.substring(it)) }
            .filter { r -> excluded.none { e -> r.first <= e.last && e.first <= r.last } }
            .toList()
        // An all-LTR message needs nothing: every line already reads left to
        // right. The pins exist so that in a bubble that mixes both, an LTR
        // line cannot be dragged around by an RTL neighbour.
        val mixed = lines.any { it.direction == Direction.RTL }
        val pinned = if (mixed) lines.filter { it.direction == Direction.LTR }.map { it.range } else emptyList()
        if (runs.isEmpty() && pinned.isEmpty()) return identity

        val insert = HashMap<Int, MutableList<Char>>()
        for (r in runs) {
            insert.getOrPut(r.first) { ArrayList(1) }.add(LRI)
            insert.getOrPut(r.last + 1) { ArrayList(1) }.add(PDI)
        }
        for (l in pinned) {
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
        return Isolated(sb.toString(), offsetMap, lines.map { it.direction })
    }
}
