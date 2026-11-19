package com.anindra.messages.ui

import java.util.Locale

/**
 * A contact as it travels over SMS: the name and one number, wrapped in the
 * smallest vCard an incoming phone can read.
 *
 * Kept free of Android types so the rule for "is this contact sendable" and the
 * card's own shape can be tested without a device.
 */
internal object ContactCard {

    /** Shortest thing a network will dial: 7 digits, as ContactsShare's callers do. */
    const val MIN_DIGITS = 7

    /** True when the number has enough digits to be worth sending. */
    fun isSendable(number: String): Boolean = number.count { it.isDigit() } >= MIN_DIGITS

    /** The label a card carries when the contact has no name saved. */
    fun nameOf(name: String?, number: String): String {
        val trimmed = name?.trim().orEmpty()
        return trimmed.ifEmpty { number.trim() }
    }

    /**
     * vCard 3.0 text, the version every stock dialer imports. Newlines inside
     * the name are folded away: a line break in the middle of a property ends
     * it, so a two-line contact name would produce a broken card.
     */
    fun vCard(name: String?, number: String): String {
        val safeName = nameOf(name, number).replace(Regex("[\\r\\n]+"), " ").replace(";", " ")
        val safeNumber = number.replace("\n", "").replace("\r", "").trim()
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$safeName")
            appendLine("TEL;TYPE=CELL:$safeNumber")
            appendLine("END:VCARD")
        }
    }

    /** Coordinates as a maps link, with a full stop as the decimal separator. */
    fun geoLink(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)

    /** A fix is usable when it is a real point on Earth. */
    fun isUsable(latitude: Double, longitude: Double): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)
}
