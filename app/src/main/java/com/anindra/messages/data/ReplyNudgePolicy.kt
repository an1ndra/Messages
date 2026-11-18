package com.anindra.messages.data

/**
 * One conversation as the nudge rule sees it (#484): whatever the row says, the
 * decision is made in [ReplyNudgePolicy] so a nudge can be reasoned about — and
 * tested — without a database, a channel or a clock.
 */
data class ReplyNudgeRow(
    val conversationId: Long,
    val address: String,
    val snippet: String,
    /** Timestamp of the newest message in the thread. */
    val newest: Long,
    val lastIsMine: Boolean,
    val unread: Int,
    val archived: Boolean,
    val blocked: Boolean,
    val deleted: Boolean
)

/**
 * When a thread is worth reminding about.
 *
 * The gap this fills is the opposite of unread: the message was read, the reply
 * was meant, and then it was forgotten. An unread thread already has its own
 * notification, so nudging it too would only double the noise — and a nudge is
 * only a reminder, so everything that asked not to be disturbed (archive,
 * block, trash, a muted channel, the master switch) wins over it.
 */
object ReplyNudgePolicy {

    /** Hours after the unanswered message, offered in Settings. */
    val INTERVAL_CHOICES = listOf(24, 48, 72)

    const val DEFAULT_INTERVAL_HOURS = 24

    /** A nudge per conversation per day at most, even if the thread stays open. */
    const val REPEAT_HOURS = 24L

    fun intervalMillis(hours: Int): Long =
        hours.coerceIn(1, 24 * 7).toLong() * HOUR_MILLIS

    fun shouldNudge(
        row: ReplyNudgeRow,
        muted: Boolean,
        lastNudgedAt: Long,
        now: Long,
        intervalMillis: Long
    ): Boolean {
        // A thread whose newest message is ours is answered by definition.
        if (row.lastIsMine) return false
        // Unread gets the ordinary message notification instead.
        if (row.unread > 0) return false
        if (row.archived || row.blocked || row.deleted || muted) return false
        if (row.newest <= 0L) return false
        // Not old enough yet: the reply may still be on its way.
        if (now - row.newest < intervalMillis) return false
        if (lastNudgedAt > 0 && now - lastNudgedAt < REPEAT_HOURS * HOUR_MILLIS) return false
        return true
    }

    private const val HOUR_MILLIS = 3_600_000L
}
