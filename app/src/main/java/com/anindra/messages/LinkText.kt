package com.anindra.messages

import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.text.util.Linkify

// Redaction runs on the composition thread so a hidden link is never painted for
// a frame (see rememberLinkedText), and the home list redacts per row per
// recomposition. Bodies are re-redacted constantly, so memoize the results.
private const val CACHE_MAX = 512

private val redactionCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
        size > CACHE_MAX
}

/**
 * Removes every web URL from [text], leaving nothing in its place. Used when the
 * user has enabled "Hide links from messages".
 */
fun hideUrls(text: String): String {
    if (text.isEmpty()) return text
    synchronized(redactionCache) { redactionCache[text]?.let { return it } }
    val redacted = redact(text)
    synchronized(redactionCache) { redactionCache[text] = redacted }
    return redacted
}

private fun redact(text: String): String {
    val spanned = SpannableStringBuilder(text)
    Linkify.addLinks(spanned, Linkify.WEB_URLS)
    val spans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
        .sortedBy { spanned.getSpanStart(it) }
    if (spans.isEmpty()) return text
    val out = StringBuilder(text.length)
    var cursor = 0
    for (span in spans) {
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        if (start >= cursor && end > start) {
            out.append(text, cursor, start)
            cursor = end
        }
    }
    if (cursor < text.length) out.append(text, cursor, text.length)
    return tidyWhitespace(out.toString())
}

private fun tidyWhitespace(s: String): String = s
    .replace(Regex("[ \\t]{2,}"), " ")
    .replace(Regex("[ \\t]+\\n"), "\n")
    .replace(Regex("\\n[ \\t]+"), "\n")
    .trim()
