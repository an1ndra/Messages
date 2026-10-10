package com.anindra.messages.mms.smil

/**
 * A slideshow as an ordered list of time containers.
 *
 * Grouping into `<par>` rather than a flat media list is not cosmetic: it decides
 * whether a caption is shown alongside its image or as a separate slide, which is
 * what the receiving handset renders. The grouping therefore lives in the model,
 * where it can be tested, instead of being rediscovered by the serializer.
 */
data class SmilDocument(
    val pars: List<SmilPar>,
    val parDurationMillis: Long = DEFAULT_PAR_DURATION_MILLIS,
    /** Read from an inbound document; never written, because we emit no `<meta>`. */
    val compatibility: String? = null,
) {
    val items: List<SmilItem> get() = pars.flatMap { it.items }

    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        const val COMPATIBILITY_96 = "96"
        const val DEFAULT_PAR_DURATION_MILLIS = 8_000L
    }
}

/** One `<par>`: the media shown together, for one duration. */
data class SmilPar(val items: List<SmilItem>)

/**
 * One media reference. The element name is fixed per media type, so only `src`
 * is caller-settable.
 */
sealed interface SmilItem {
    val src: String
    val elementName: String

    companion object {
        /** Element name to item type. SMIL spells the image element `img`. */
        fun forElementName(elementName: String, src: String): SmilItem? = when (elementName) {
            "text", "vcard" -> SmilText(src)
            "img" -> SmilImage(src)
            "audio" -> SmilAudio(src)
            "video" -> SmilVideo(src)
            else -> null
        }
    }
}

data class SmilText(override val src: String) : SmilItem {
    override val elementName: String get() = "text"
}

data class SmilImage(override val src: String) : SmilItem {
    override val elementName: String get() = "img"
}

data class SmilAudio(override val src: String) : SmilItem {
    override val elementName: String get() = "audio"
}

data class SmilVideo(override val src: String) : SmilItem {
    override val elementName: String get() = "video"
}

/**
 * SMIL clock values. An unadorned number is seconds, so `5` and `5s` both mean
 * five seconds and neither means five milliseconds.
 */
internal object SmilClock {

    private val NUMBER = Regex("^(\\d+(?:\\.\\d+)?)(ms|s|min|h)?$")
    private val CLOCK_TIME = Regex("^(\\d+):([0-5]\\d):([0-5]\\d(?:\\.\\d+)?)$")

    /**
     * Writes whole milliseconds, e.g. `8000ms`. SMIL 3.0 would allow a fractional
     * timecount, but MMS 1.3 requires integer milliseconds, and that is the form
     * carriers are being sent today.
     */
    fun format(millis: Long): String = "${millis.coerceAtLeast(0L)}ms"

    fun parse(value: String?): Long? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        CLOCK_TIME.matchEntire(text)?.let { match ->
            val (hours, minutes, seconds) = match.destructured
            return ((hours.toLong() * 60L + minutes.toLong()) * 60L + seconds.toDouble().toLong()) * 1_000L
        }
        val match = NUMBER.matchEntire(text) ?: return null
        val amount = match.groupValues[1].toDouble()
        return when (match.groupValues[2]) {
            "ms" -> amount.toLong()
            "min" -> (amount * 60_000.0).toLong()
            "h" -> (amount * 3_600_000.0).toLong()
            else -> (amount * 1_000.0).toLong()
        }
    }
}
