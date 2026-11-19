package com.anindra.messages.data

import androidx.annotation.StringRes
import com.anindra.messages.R
import java.util.Locale

/**
 * The one-tap replies offered above the composer and on the notification
 * (#483). Each is a fixed phrase, so it lives in the resources and translates;
 * the heuristic below only decides *which* phrases fit the message it is
 * answering.
 */
enum class ReplySuggestion(@StringRes val label: Int) {
    YES(R.string.suggestion_yes),
    NO(R.string.suggestion_no),
    SURE(R.string.suggestion_sure),
    LET_ME_CHECK(R.string.suggestion_let_me_check),
    YOURE_WELCOME(R.string.suggestion_youre_welcome),
    NO_PROBLEM(R.string.suggestion_no_problem),
    ON_MY_WAY(R.string.suggestion_on_my_way),
    ALMOST_THERE(R.string.suggestion_almost_there),
    CALLING_NOW(R.string.suggestion_calling_now),
    CANT_TALK(R.string.suggestion_cant_talk),
    SOUNDS_GOOD(R.string.suggestion_sounds_good),
    CANT_MAKE_IT(R.string.suggestion_cant_make_it),
    HELLO(R.string.suggestion_hello),
    GOT_IT(R.string.suggestion_got_it)
}

/**
 * A small local guess at what a reply would say. No model, no network, no
 * dependency: the phrases that cover most of what gets typed on a phone are
 * also the ones a few patterns can recognise (a yes/no question, thanks, an
 * arrival, a call, a plan).
 *
 * Deliberately quiet: a plain statement suggests nothing, because a wrong
 * suggestion is worse than none, and the chips are only a shortcut for text the
 * user was going to type anyway.
 */
object SuggestedReplies {

    const val MAX = 3

    private val YES_NO_START = Regex(
        "^(are|is|was|were|do|does|did|can|could|will|would|shall|should|have|has|" +
            "am|may|might|aren't|isn't|don't|doesn't|didn't|can't|won't|" +
            "you|u|r|ru)\\b"
    )

    private val THANKS = listOf("thank", "thanks", "thx", "ty ", "appreciate")
    private val ARRIVAL = listOf(
        "on my way", "on the way", "coming", "almost there", "be there", "arriving",
        "eta", "here now", "outside", "at the door", "downstairs", "in the car"
    )
    private val CALL = listOf("call me", "phone me", "ring me", "call you", "can you call")
    private val TIME_OR_PLAN = listOf(
        "tonight", "tomorrow", "today", "this evening", "this morning", "later",
        "dinner", "lunch", "meeting", "meet", "free at", "at noon", "this weekend"
    )
    private val GREETING = Regex("^(hi|hey|hello|yo|good morning|good evening|good afternoon)\\b")

    /** The replies worth offering for one incoming message, at most [MAX]. */
    fun forMessage(text: String?): List<ReplySuggestion> {
        val body = text?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (body.isEmpty()) return emptyList()
        return when {
            // Yes/no questions come first: "Are we still on for dinner?" should
            // not turn into "Sounds good".
            body.contains('?') && YES_NO_START.containsMatchIn(body) -> listOf(
                ReplySuggestion.YES, ReplySuggestion.NO
            )
            body.contains("where are you") || body.contains("where r u") -> listOf(
                ReplySuggestion.ON_MY_WAY, ReplySuggestion.ALMOST_THERE
            )
            THANKS.any { body.contains(it) } -> listOf(
                ReplySuggestion.YOURE_WELCOME, ReplySuggestion.NO_PROBLEM
            )
            ARRIVAL.any { body.contains(it) } -> listOf(
                ReplySuggestion.ON_MY_WAY, ReplySuggestion.ALMOST_THERE
            )
            CALL.any { body.contains(it) } -> listOf(
                ReplySuggestion.CALLING_NOW, ReplySuggestion.CANT_TALK
            )
            GREETING.containsMatchIn(body) -> listOf(
                ReplySuggestion.HELLO, ReplySuggestion.GOT_IT
            )
            TIME_OR_PLAN.any { body.contains(it) } -> listOf(
                ReplySuggestion.SOUNDS_GOOD, ReplySuggestion.CANT_MAKE_IT
            )
            body.contains('?') -> listOf(
                ReplySuggestion.SURE, ReplySuggestion.LET_ME_CHECK
            )
            else -> emptyList()
        }.take(MAX)
    }

    /**
     * The suggestions for a thread, given its messages newest-first: only while
     * the newest message is an unanswered text of theirs. A thread whose last
     * word is ours is answered, and a photo is not a question.
     */
    fun forThread(messages: List<Message>, enabled: Boolean): List<ReplySuggestion> {
        if (!enabled) return emptyList()
        val newest = messages.firstOrNull() ?: return emptyList()
        if (newest.isMe) return emptyList()
        if (newest.mediaType != "text") return emptyList()
        return forMessage(newest.body)
    }
}
