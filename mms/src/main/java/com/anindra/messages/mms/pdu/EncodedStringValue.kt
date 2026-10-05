package com.anindra.messages.mms.pdu

/**
 * A charset plus the raw bytes of some text.
 *
 * The bytes are kept undecoded on purpose. `text` is a convenience view, but the
 * wire form is what round-trips: a sender's Shift-JIS bytes must go back out as
 * Shift-JIS, and decoding to a Kotlin String and re-encoding as UTF-8 on the way
 * out would silently rewrite every non-ASCII message we send.
 */
class EncodedStringValue(val charsetMibEnum: Int, val textBytes: ByteArray) {

    constructor(charsetMibEnum: Int, text: String) : this(
        charsetMibEnum,
        text.toByteArray(CharacterSets.charsetFor(charsetMibEnum)),
    )

    val charsetName: String get() = CharacterSets.mimeName(charsetMibEnum)

    /** Decoded view of [textBytes]; malformed bytes become replacement chars. */
    val text: String
        get() = String(textBytes, CharacterSets.charsetFor(charsetMibEnum))

    /**
     * Bytes this value occupies inside its own value-length: the charset
     * short-integer, an optional quote, the text, and the terminator.
     */
    fun encodedLength(): Int {
        var length = 1 + textBytes.size + 1
        if (textBytes.isNotEmpty() && textBytes[0].toInt() and 0xFF > WspWriter.TEXT_STRING_QUOTE) length++
        return length
    }

    fun withText(text: String): EncodedStringValue =
        EncodedStringValue(charsetMibEnum, text)

    override fun equals(other: Any?): Boolean =
        other is EncodedStringValue &&
            charsetMibEnum == other.charsetMibEnum &&
            textBytes.contentEquals(other.textBytes)

    override fun hashCode(): Int = 31 * charsetMibEnum + textBytes.contentHashCode()

    override fun toString(): String = "EncodedStringValue($charsetName, ${textBytes.size}B)"

    companion object {
        const val ADDRESS_PRESENT_TOKEN = 0x80
        const val INSERT_ADDRESS_TOKEN = 0x81

        const val ADDRESS_PRESENT_TOKEN_STR = "address-present-token"
        const val INSERT_ADDRESS_TOKEN_STR = "insert-address-token"

        fun utf8(text: String) = EncodedStringValue(CharacterSets.UTF_8, text)

        /**
         * A From field with no address: the one-byte insert-address-token form.
         * Carriers use this when they fill the sender in from the SIM.
         */
        fun insertAddressToken() = EncodedStringValue(
            INSERT_ADDRESS_TOKEN,
            INSERT_ADDRESS_TOKEN_STR.toByteArray(Charsets.US_ASCII),
        )
    }
}
