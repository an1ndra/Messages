package com.anindra.messages.mms.pdu

/**
 * The IANA MIBenum values MMS uses for `Encoded-string-value`, and the MIME
 * charset names they map to. The table is sparse: only the encodings MMS
 * actually specifies are listed, and anything else falls back to UTF-8 on
 * decode rather than failing the whole PDU.
 */
object CharacterSets {
    const val ANY_CHARSET = 0x00
    const val US_ASCII = 0x03
    const val ISO_8859_1 = 0x04
    const val ISO_8859_2 = 0x05
    const val ISO_8859_3 = 0x06
    const val ISO_8859_4 = 0x07
    const val ISO_8859_5 = 0x08
    const val ISO_8859_6 = 0x09
    const val ISO_8859_7 = 0x0A
    const val ISO_8859_8 = 0x0B
    const val ISO_8859_9 = 0x0C
    const val SHIFT_JIS = 0x11
    const val UTF_8 = 0x6A
    const val BIG5 = 0x07EA
    const val UCS2 = 0x03E8
    const val UTF_16 = 0x03F7

    const val DEFAULT_CHARSET = UTF_8

    const val NAME_ANY = "*"
    const val NAME_US_ASCII = "us-ascii"
    const val NAME_ISO_8859_1 = "iso-8859-1"
    const val NAME_ISO_8859_2 = "iso-8859-2"
    const val NAME_ISO_8859_3 = "iso-8859-3"
    const val NAME_ISO_8859_4 = "iso-8859-4"
    const val NAME_ISO_8859_5 = "iso-8859-5"
    const val NAME_ISO_8859_6 = "iso-8859-6"
    const val NAME_ISO_8859_7 = "iso-8859-7"
    const val NAME_ISO_8859_8 = "iso-8859-8"
    const val NAME_ISO_8859_9 = "iso-8859-9"
    const val NAME_SHIFT_JIS = "shift_JIS"
    const val NAME_UTF_8 = "utf-8"
    const val NAME_BIG5 = "big5"
    const val NAME_UCS2 = "iso-10646-ucs-2"
    const val NAME_UTF_16 = "utf-16"

    const val DEFAULT_CHARSET_NAME = NAME_UTF_8

    private val MIBENUM_TO_NAME = mapOf(
        ANY_CHARSET to NAME_ANY,
        US_ASCII to NAME_US_ASCII,
        ISO_8859_1 to NAME_ISO_8859_1,
        ISO_8859_2 to NAME_ISO_8859_2,
        ISO_8859_3 to NAME_ISO_8859_3,
        ISO_8859_4 to NAME_ISO_8859_4,
        ISO_8859_5 to NAME_ISO_8859_5,
        ISO_8859_6 to NAME_ISO_8859_6,
        ISO_8859_7 to NAME_ISO_8859_7,
        ISO_8859_8 to NAME_ISO_8859_8,
        ISO_8859_9 to NAME_ISO_8859_9,
        SHIFT_JIS to NAME_SHIFT_JIS,
        UTF_8 to NAME_UTF_8,
        BIG5 to NAME_BIG5,
        UCS2 to NAME_UCS2,
        UTF_16 to NAME_UTF_16,
    )

    private val NAME_TO_MIBENUM = MIBENUM_TO_NAME.entries.associate { (k, v) -> v to k }

    fun mimeName(mibEnum: Int): String =
        MIBENUM_TO_NAME[mibEnum] ?: DEFAULT_CHARSET_NAME

    fun mibEnum(mimeName: String): Int =
        NAME_TO_MIBENUM[mimeName.trim().lowercase()] ?: DEFAULT_CHARSET

    /**
     * Resolves the Charset for a MIBenum, falling back to UTF-8 for a value this
     * table does not carry. A carrier declaring a charset we lack must not stop
     * the message from being read.
     */
    fun charsetFor(mibEnum: Int): java.nio.charset.Charset =
        runCatching { java.nio.charset.Charset.forName(mimeName(mibEnum)) }
            .getOrDefault(java.nio.charset.StandardCharsets.UTF_8)
}
