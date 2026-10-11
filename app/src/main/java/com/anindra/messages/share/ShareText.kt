package com.anindra.messages.share

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Reads a message body another app handed over in a share intent.
 *
 * Two encoding faults show up in the wild and neither is ours to fix at the
 * source. A sender may escape the `?body=` value twice, so one decode pass
 * leaves a literal `%D9%85` in the composer; and a sender that built its URI
 * by mapping UTF-8 bytes onto ISO-8859-1 chars ships a body that reads as
 * `Ù…Ø±Ù†` instead of Persian. Iranian bank apps do the latter on their
 * "send receipt by SMS" button, so a body is repaired on the way in.
 */
object ShareText {

    /**
     * The `body` value of a share URI's query, still percent-encoded.
     *
     * Returns null when the query carries no `body` parameter or it is empty.
     */
    fun bodyFromQuery(encodedQuery: String?): String? {
        if (encodedQuery.isNullOrBlank()) return null
        val value = encodedQuery.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.getOrNull(0) == "body" }
            ?.getOrNull(1)
            ?: return null
        return percentDecode(value).trim().takeIf { it.isNotBlank() }
    }

    /**
     * Percent-decoding, applied twice.
     *
     * `getQueryParameter` and `Uri.decode` only undo one layer, so a value the
     * sender escaped twice reaches the composer as `%D9%85` and is then sent as
     * those eight literal characters. Decoding again is a no-op unless escapes
     * survive the first pass. `+` is left alone: this is a URI, not an HTML form.
     */
    fun percentDecode(value: String): String {
        val once = decodeOnce(value)
        return if (once.indexOf('%') < 0) once else decodeOnce(once)
    }

    /** One decoding pass. A malformed escape is kept verbatim rather than
     *  dropped, so a body containing a bare `%` survives intact. */
    private fun decodeOnce(value: String): String {
        val out = StringBuilder(value.length)
        val run = ArrayList<Byte>()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            val escaped = c == '%' && i + 2 < value.length &&
                hexValue(value[i + 1]) >= 0 && hexValue(value[i + 2]) >= 0
            if (escaped) {
                run.add((((hexValue(value[i + 1]) shl 4) or hexValue(value[i + 2]))).toByte())
                i += 3
                continue
            }
            flush(out, run)
            out.append(c)
            i++
        }
        flush(out, run)
        return out.toString()
    }

    /**
     * Emits a run of consecutive escapes as one character sequence.
     *
     * Consecutive escapes are one character, not several: a Persian letter is
     * `%D9%85`, and decoding byte by byte would turn it into two Latin-1
     * letters — exactly the mojibake this class exists to undo. A run that is
     * not valid UTF-8 is emitted as Latin-1 so nothing is lost.
     */
    private fun flush(out: StringBuilder, run: ArrayList<Byte>) {
        if (run.isEmpty()) return
        val bytes = run.toByteArray()
        out.append(decodeStrict(bytes) ?: String(bytes, StandardCharsets.ISO_8859_1))
        run.clear()
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    /**
     * Undoes a body whose UTF-8 bytes were read as ISO-8859-1 one byte per char.
     *
     * Only fires when the result is unambiguous: the text must still be pure
     * Latin-1, must not already contain a non-Latin script, and must turn back
     * into one that does. `Café` therefore stays untouched — `E9` alone is not
     * valid UTF-8 — while the bank receipt comes out readable.
     */
    fun repairMojibake(text: String): String {
        if (text.isEmpty()) return text
        if (text.any { it.code > 0xFF }) return text
        if (isNonLatinLetter(text)) return text
        if (text.none { it.code > 0x7F }) return text
        val bytes = text.toByteArray(StandardCharsets.ISO_8859_1)
        val repaired = decodeStrict(bytes) ?: return text
        return if (isNonLatinLetter(repaired)) repaired else text
    }

    /** True for a letter from any script other than Latin, so real Persian,
     *  Cyrillic or CJK text is recognised in either the mangled or the clean form. */
    private fun isNonLatinLetter(text: String): Boolean = text.any { c ->
        Character.isLetter(c) && !isLatinLetter(c)
    }

    /**
     * Latin as Unicode numbers it: Basic Latin, Latin-1 Supplement and Latin
     * Extended-A end at `024F`, and Latin Extended-B is `1E00`..`1EFF`.
     * Bounding the range this way matters — `ª` and `º` are letters sitting in
     * Latin-1 Supplement at `00AA`/`00BA`, well below `00C0`, and Persian
     * receipts produce both, so a range starting at `00C0` reads mangled text
     * as non-Latin and the repair never fires.
     */
    private fun isLatinLetter(c: Char): Boolean =
        c.code < 0x0250 || (c.code in 0x1E00..0x1EFF)

    /** Decodes as UTF-8 or returns null; a malformed sequence must not be
     *  replaced with U+FFFD, which would replace one wrong body with another. */
    private fun decodeStrict(bytes: ByteArray): String? = try {
        val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }
}
