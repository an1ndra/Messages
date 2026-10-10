package com.anindra.messages.mms.smil

/**
 * Writes a [SmilDocument] as SMIL 2.0.
 *
 * Hand-built rather than DOM-based: the output is a fixed handful of tags, and
 * attribute order has to stay put for the bytes to be comparable in tests.
 *
 * The shape is deliberately the one the AOSP `SmilHelper` produced, since that is
 * what carriers and handsets have been accepting from this app: an empty
 * `<layout>`, no `<meta>`, and no `region` attributes. Adding region geometry is
 * valid SMIL but is a change to the send path that nothing here can validate
 * against a real carrier, so it is left out. [SmilParser] still reads documents
 * that carry regions, because inbound messages do.
 */
object SmilSerializer {

    private const val XMLNS = "http://www.w3.org/2001/SMIL20/Language"

    fun serialize(document: SmilDocument): ByteArray =
        buildString { write(document) }.toByteArray(Charsets.UTF_8)

    private fun StringBuilder.write(document: SmilDocument) {
        val duration = escape(SmilClock.format(document.parDurationMillis))
        append("<smil xmlns=\"").append(escape(XMLNS)).append("\">")
        append("<head><layout/></head>")
        append("<body>")
        document.pars.forEach { par ->
            append("<par dur=\"").append(duration).append("\">")
            par.items.forEach { item ->
                append('<').append(item.elementName)
                    .append(" src=\"").append(escape(item.src)).append("\"/>")
            }
            append("</par>")
        }
        append("</body>")
        append("</smil>")
    }

    /**
     * `&` first, or it escapes its own output. Apostrophes are left alone: they
     * cannot terminate a double-quoted attribute value, and `&apos;` is not
     * defined in XML 1.0.
     */
    internal fun escape(value: String): String {
        val escaped = StringBuilder(value.length)
        value.forEach { character ->
            when (character) {
                '&' -> escaped.append("&amp;")
                '<' -> escaped.append("&lt;")
                '>' -> escaped.append("&gt;")
                '"' -> escaped.append("&quot;")
                else -> escaped.append(character)
            }
        }
        return escaped.toString()
    }
}
