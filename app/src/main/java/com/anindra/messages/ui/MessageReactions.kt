package com.anindra.messages.ui

/**
 * Local, UI-only message reactions (issue #188). A reaction never leaves the
 * device as a reaction; [toggle] just edits the per-message emoji map that is
 * stored in the message row.
 */
object MessageReactions {

    /** The emoji the reaction bar offers, shared with the composer's emoji panel. */
    val EMOJI = listOf("👍", "😂", "❤️", "🔥", "😢", "😮", "🙏", "🎉")

    /**
     * Tap-to-toggle: an emoji is either on the message (count 1) or absent.
     * Any other reactions — including counts carried in from an import — are
     * left untouched.
     */
    fun toggle(current: Map<String, Int>, emoji: String): Map<String, Int> =
        if (current.containsKey(emoji)) current - emoji else current + (emoji to 1)

    /**
     * Display order for the chips: the bar's own order first, then anything
     * else a message carries (e.g. an imported `👍:2`), so chips never shuffle
     * between recompositions.
     */
    fun ordered(reactions: Map<String, Int>): List<Pair<String, Int>> {
        val known = EMOJI.filter { reactions.containsKey(it) }
            .map { it to reactions.getValue(it) }
        val extra = reactions.filterKeys { it !in EMOJI }
            .map { it.key to it.value }
        return known + extra
    }

    /** A short single-line quote of the reacted-to body for the SMS fallback. */
    fun quote(body: String): String =
        body.replace(Regex("\\s+"), " ").trim().take(60)
}
