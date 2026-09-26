package com.anindra.messages.sms

/**
 * Static SMIL 2.0 presentation for outgoing MMS.
 *
 * The composed document is byte-for-byte what AOSP's `SmilHelper` produced for
 * the two shapes this app sends (attachment alone, attachment plus caption), so
 * the presentation part stays identical while dropping the 31-file
 * `org.w3c.dom.smil` tree.
 */
internal object MmsSmil {

    private const val PREFIX =
        "<smil xmlns=\"http://www.w3.org/2001/SMIL20/Language\">" +
            "<head><layout/></head><body><par dur=\"8000ms\">"
    private const val SUFFIX = "</par></body></smil>"

    /**
     * @param mediaSrc `src` of the media element, matching the attachment's
     *   content-location
     * @param textSrc `src` of the caption element, or null when there is no
     *   caption part
     */
    fun document(mediaSrc: String, mediaTag: String, textSrc: String?): String {
        val media = "<$mediaTag src=\"${escape(mediaSrc)}\"/>"
        val text = textSrc?.let { "<text src=\"${escape(it)}\"/>" } ?: ""
        return PREFIX + media + text + SUFFIX
    }

    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
