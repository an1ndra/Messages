package com.anindra.messages.data

/**
 * The SMS text one of our devices sends when it reacts, and the parser the
 * receiving device uses to turn it back into a reaction (issue #188, app to
 * app).
 *
 * The format is deliberately English and stable rather than localized: both
 * ends have to agree on it, and a recipient without the app simply reads it. It
 * is quote-free so a plain `sms send` console command can carry it, and the
 * quoted snippet is how the receiver finds the message being reacted to — SMS
 * carries no reply/quote metadata.
 */
object ReactionFallback {

    data class Parsed(val emoji: String, val snippet: String, val added: Boolean)

    private val ADD = Regex("^Reacted (\\S+) to (.*)$")
    private val REMOVED = Regex("^Removed (\\S+) from (.*)$")

    fun add(emoji: String, snippet: String): String = "Reacted $emoji to $snippet"

    fun remove(emoji: String, snippet: String): String = "Removed $emoji from $snippet"

    /** Null when [body] is an ordinary message, not one of our reaction notices. */
    fun parse(body: String): Parsed? {
        ADD.find(body)?.let {
            val emoji = it.groupValues[1]
            if (looksLikeEmoji(emoji)) return Parsed(emoji, it.groupValues[2], added = true)
        }
        REMOVED.find(body)?.let {
            val emoji = it.groupValues[1]
            if (looksLikeEmoji(emoji)) return Parsed(emoji, it.groupValues[2], added = false)
        }
        return null
    }

    /** An emoji is non-ASCII; this keeps a sentence like "Removed you from the
     *  group" from being mistaken for a reaction. */
    private fun looksLikeEmoji(token: String): Boolean = token.any { it.code > 0x7F }
}
