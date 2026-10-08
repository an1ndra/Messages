package com.anindra.messages.data

/**
 * The SMS text one of our devices sends when the user reacts (issue #188).
 *
 * The receiving side deliberately does not parse it: the notice arrives as an
 * ordinary message and reads as one. It is English and stable rather than
 * localized so a recipient without the app still understands it, and it is
 * quote-free so a plain `sms send` console command can carry it.
 */
object ReactionFallback {

    fun add(emoji: String, snippet: String): String = "Reacted $emoji to $snippet"

    fun remove(emoji: String, snippet: String): String = "Removed $emoji from $snippet"
}
