package com.anindra.messages.data

/**
 * The quote-free notice our reactions send (issue #188) is left as an ordinary
 * message on arrival. iOS wraps the quoted text in curly quotes and names some
 * tapbacks in words; those notices are applied to the quoted message, since
 * SMS carries no reaction metadata of its own.
 */
object ReactionFallback {

    data class Parsed(val emoji: String, val snippet: String, val added: Boolean)

    private val ADD = Regex("^Reacted (\\S+) to (.+)$")
    private val REMOVED = Regex("^Removed (\\S+) from (.+)$")
    private val IOS_ADD = Regex("^(Liked|Loved|Disliked|Laughed at|Questioned|Emphasized) (.+)$")
    private val IOS_REMOVE =
        Regex("^Removed (a heart|a like|a dislike|a laugh|an exclamation|a question mark) from (.+)$")

    private val IOS_EMOJI = mapOf(
        "Liked" to "👍",
        "Loved" to "❤️",
        "Disliked" to "👎",
        "Laughed at" to "😂",
        "Questioned" to "❓",
        "Emphasized" to "❗",
        "a heart" to "❤️",
        "a like" to "👍",
        "a dislike" to "👎",
        "a laugh" to "😂",
        "an exclamation" to "❗",
        "a question mark" to "❓"
    )

    fun add(emoji: String, snippet: String): String = "Reacted $emoji to $snippet"

    fun remove(emoji: String, snippet: String): String = "Removed $emoji from $snippet"

    /** Null when [body] is an ordinary message or one of our own quote-free notices. */
    fun parse(body: String): Parsed? {
        ADD.matchEntire(body)?.let { m ->
            val emoji = m.groupValues[1]
            if (looksLikeEmoji(emoji)) {
                unquote(m.groupValues[2])?.let { return Parsed(emoji, it, added = true) }
            }
        }
        REMOVED.matchEntire(body)?.let { m ->
            val emoji = m.groupValues[1]
            if (looksLikeEmoji(emoji)) {
                unquote(m.groupValues[2])?.let { return Parsed(emoji, it, added = false) }
            }
        }
        IOS_ADD.matchEntire(body)?.let { m ->
            IOS_EMOJI[m.groupValues[1]]?.let { emoji ->
                unquote(m.groupValues[2], curlyOnly = true)
                    ?.let { return Parsed(emoji, it, added = true) }
            }
        }
        IOS_REMOVE.matchEntire(body)?.let { m ->
            IOS_EMOJI[m.groupValues[1]]?.let { emoji ->
                unquote(m.groupValues[2], curlyOnly = true)
                    ?.let { return Parsed(emoji, it, added = false) }
            }
        }
        return null
    }

    /** An emoji is non-ASCII; this keeps a sentence like "Removed you from the
     *  group" from being mistaken for a reaction. */
    private fun looksLikeEmoji(token: String): Boolean = token.any { it.code > 0x7F }

    /**
     * Apple wraps the target in curly quotes (“…”), sometimes with spaces just
     * inside them. The named tapbacks only accept those: a plain text like
     * `Loved "Dune"` is the user's own sentence, not a tapback.
     */
    private fun unquote(raw: String, curlyOnly: Boolean = false): String? {
        val s = raw.trim().trim('\u200A', '\u200B', '\u200C', '\u200E', '\u200F')
        if (s.length < 2) return null
        val open = s.first()
        val close = s.last()
        val curly = open == '“' && close == '”'
        val straight = !curlyOnly && open == '"' && close == '"'
        if (!curly && !straight) return null
        return s.substring(1, s.length - 1).trim().ifEmpty { null }
    }
}
