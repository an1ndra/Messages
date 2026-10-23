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
     *  Whitespace is horizontal only: a line break ends a run, so one message's
     *  amount is never isolated together with the next line's timestamp. */
    private val NUMBER_RUN = Regex("""[-+]?\(?[$DIGITS][$DIGITS \t()./$NBSP,_:\u060C\u066B\u066C-]*[$DIGITS]\)?""")

    private fun needsIsolation(run: String): Boolean = run.any { !it.isDigit() }

    /** Unicode P2: the paragraph direction is set by its first strong
     *  character. No strong character (a bare number) counts as LTR. */
    private fun isParagraphRtl(text: String): Boolean {
        for (c in text) {
            when (Character.getDirectionality(c)) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            }
        }
        return false
    }

    /** [value] as a single LTR run; idempotent. */
    fun ltr(value: String): String {
        if (value.isEmpty() || (value.first() == LRI && value.last() == PDI)) return value
        return "$LRI$value$PDI"
    }

    /** Isolated [text] plus a boundary map enabling [remap] of styled ranges. */
    class Isolated internal constructor(val text: String, private val offsetMap: IntArray) {
        /** Maps an inclusive range in the original string to its inclusive
         *  counterpart in [text], absorbing any isolates inserted at its edges. */
        fun remap(range: IntRange): IntRange =
            offsetMap[range.first]..(offsetMap[range.last + 1] - 1)
    }

    /** Isolates every number-like run in [text]. Runs intersecting [excluded]
     *  (e.g. digits inside a link) are left untouched. No-op for LTR text: a
     *  single LTR run already keeps its order, and splitting it into several
     *  isolated runs would reorder them in an RTL layout. */
    fun isolateNumberRuns(text: String, excluded: List<IntRange> = emptyList()): Isolated {
        if (!isParagraphRtl(text)) return Isolated(text, IntArray(text.length + 1) { it })
        val runs = NUMBER_RUN.findAll(text)
            .map { it.range }
            .filter { needsIsolation(text.substring(it)) }
            .filter { r -> excluded.none { e -> r.first <= e.last && e.first <= r.last } }
            .toList()
        if (runs.isEmpty()) return Isolated(text, IntArray(text.length + 1) { it })

        val offsetMap = IntArray(text.length + 1)
        val sb = StringBuilder(text.length + runs.size * 2)
        var o = 0
        var n = 0
        var mi = 0
        while (o < text.length) {
            val run = runs.getOrNull(mi)
            if (run != null && o == run.first) {
                offsetMap[o] = n
                sb.append(LRI); n++
                for (i in run) {
                    offsetMap[i + 1] = n
                    sb.append(text[i]); n++
                }
                sb.append(PDI); n++
                offsetMap[run.last + 1] = n
                o = run.last + 1
                mi++
            } else {
                offsetMap[o] = n
                sb.append(text[o]); n++
                o++
            }
        }
        offsetMap[text.length] = n
        return Isolated(sb.toString(), offsetMap)
    }
}
