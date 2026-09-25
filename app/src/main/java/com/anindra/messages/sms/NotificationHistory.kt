package com.anindra.messages.sms

/** One line of a grouped notification. */
data class NotificationLine(
    val text: String,
    val timestamp: Long,
    val fromMe: Boolean
)

/**
 * A grouped notification is rebuilt from scratch on every post, because
 * `notify(id, …)` replaces the record rather than appending to it. So the
 * history has to be re-read and re-added each time, and only the tail is worth
 * carrying: the system renders a handful of lines when expanded, and older ones
 * cost notification size for nothing.
 */
object NotificationHistory {
    const val MAX_LINES = 5

    /** The newest [MAX_LINES], oldest first, which is the order
     *  MessagingStyle expects. Blank lines are dropped: the system renders an
     *  empty line as an empty row. */
    fun window(lines: List<NotificationLine>): List<NotificationLine> =
        lines.filter { it.text.isNotBlank() }
            .takeLast(MAX_LINES)

    /** How many of their messages to carry. Bounded by what is actually
     *  unread, so a long-conversation read earlier does not repopulate the
     *  notification with old lines. At least one line, because the message that
     *  triggered the post is itself unread and must appear. */
    fun takeCount(unread: Int, maxLines: Int): Int = when {
        unread <= 0 -> 1
        unread >= maxLines -> maxLines
        else -> unread
    }

    /** What the collapsed notification reads as. */
    fun summary(count: Int, sender: String): String = when (count) {
        0 -> sender
        1 -> sender
        else -> "$count new messages"
    }
}
