package com.anindra.messages.ui

/** Display rules for the conversation-details screen.
 *
 *  A saved contact shows its name as the title with the formatted number
 *  beneath it; an unknown sender shows the formatted number as the title and
 *  has no subtitle. [address] is the canonical address and [display] its
 *  locale-formatted form. */
object ContactDetails {

    fun isKnown(name: String, address: String): Boolean = name != address

    fun title(name: String, address: String, display: String): String =
        if (isKnown(name, address)) name else display

    fun subtitle(name: String, address: String, display: String): String? =
        if (isKnown(name, address)) display else null
}
