package com.anindra.messages.data

/**
 * Retention for everything the app files away. Trash used to be the only
 * covered bucket, so keyword-blocked messages and blocked senders accumulated
 * forever.
 *
 * The predicates carry no table alias on purpose: Android's SQLite rejects
 * `DELETE FROM messages m WHERE ...`, and a startup crash here would take the
 * whole purge down. Every column used is unambiguous within its own table.
 */
object RetentionPolicy {
    const val DEFAULT_DAYS = 30
    val DAY_OPTIONS = listOf(7, 30, 90, 365)

    fun normalizedDays(days: Int): Int = if (days in DAY_OPTIONS) days else DEFAULT_DAYS

    fun cutoff(now: Long, days: Int): Long =
        now - normalizedDays(days) * 24L * 60 * 60 * 1000

    /** Trashed threads: aged by when they were trashed. */
    const val TRASHED_SQL = "deleted_at>0 AND deleted_at<?"

    /** Keyword-blocked messages, which never touch their conversation. */
    const val KEYWORD_BLOCKED_SQL = "deleted_at>0 AND blocked_reason!='' AND deleted_at<?"

    /** Blocked senders have no deleted_at, so their last activity stands in for
     *  age - a sender who keeps texting is not purged out from under the user. */
    const val BLOCKED_CONVERSATION_SQL = "blocked=1 AND timestamp<?"
}
