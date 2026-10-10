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

    /**
     * The name a conversation is listed and announced under.
     *
     * A group is named by its members, not by whoever it started with: its
     * `address` is the primary contact's, so falling through to [title] would
     * file "Sarah + Dad" under "Sarah" -- in the list row, and in what a
     * screen reader announces for it.
     *
     * Lives here rather than in either list so the two cannot disagree, which
     * is exactly how the redesigned list lost it: the legacy list had this
     * rule and the redesigned one was written without it.
     */
    fun listLabel(
        groupTitle: String,
        name: String,
        address: String,
        display: String
    ): String = if (groupTitle.isNotBlank()) groupTitle else title(name, address, display)
}
