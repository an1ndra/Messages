package com.anindra.messages.data

/** What the user has asked to be cleaned up automatically. */
enum class RetentionBucket { TRASH, KEYWORD_MESSAGES, BLOCKED_SENDERS }

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

    /** Trash and Spam & Blocked keep things for their own span, because the
     *  user sets each one from the folder it applies to. Both Spam buckets share
     *  one span: they are one folder, so one number describes it. */
    fun daysFor(bucket: RetentionBucket, trashDays: Int, spamDays: Int): Int =
        normalizedDays(if (bucket == RetentionBucket.TRASH) trashDays else spamDays)

    /** Trashed threads: aged by when they were trashed. */
    const val TRASHED_SQL = "deleted_at>0 AND deleted_at<?"

    /** Keyword-blocked messages, which never touch their conversation. */
    const val KEYWORD_BLOCKED_SQL = "deleted_at>0 AND blocked_reason!='' AND deleted_at<?"

    /** Blocked senders have no deleted_at, so their last activity stands in for
     *  age - a sender who keeps texting is not purged out from under the user. */
    const val BLOCKED_CONVERSATION_SQL = "blocked=1 AND timestamp<?"

    fun cutoffFor(bucket: RetentionBucket, now: Long, trashDays: Int, spamDays: Int): Long =
        now - daysFor(bucket, trashDays, spamDays) * 24L * 60 * 60 * 1000

    fun sql(bucket: RetentionBucket): String = when (bucket) {
        RetentionBucket.TRASH -> TRASHED_SQL
        RetentionBucket.KEYWORD_MESSAGES -> KEYWORD_BLOCKED_SQL
        RetentionBucket.BLOCKED_SENDERS -> BLOCKED_CONVERSATION_SQL
    }

    /** Empty when auto-delete is off entirely, or when every bucket is off. */
    fun activeBuckets(
        enabled: Boolean,
        trash: Boolean,
        keywordMessages: Boolean,
        blockedSenders: Boolean
    ): Set<RetentionBucket> {
        if (!enabled) return emptySet()
        val out = mutableSetOf<RetentionBucket>()
        if (trash) out += RetentionBucket.TRASH
        if (keywordMessages) out += RetentionBucket.KEYWORD_MESSAGES
        if (blockedSenders) out += RetentionBucket.BLOCKED_SENDERS
        return out
    }
}
