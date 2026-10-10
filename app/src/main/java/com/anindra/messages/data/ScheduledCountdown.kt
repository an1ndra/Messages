package com.anindra.messages.data

import java.util.concurrent.TimeUnit

/**
 * The "in 2h 14m" suffix on a message waiting on the scheduler.
 *
 * Kept separate from the bubble so the arithmetic is testable without a clock:
 * every format takes `now` explicitly, and the composable supplies it.
 */
object ScheduledCountdown {

    /** Milliseconds left until [target]; zero or negative once it has passed. */
    fun remainingMillis(now: Long, target: Long): Long = (target - now).coerceAtLeast(0L)

    /**
     * Coarse units only. A scheduled message is minutes or hours away, so
     * ticking a visible seconds column would recompose the row for no gain.
     */
    fun format(now: Long, target: Long): String {
        val ms = remainingMillis(now, target)
        if (ms == 0L) return ""

        val days = TimeUnit.MILLISECONDS.toDays(ms)
        val hours = TimeUnit.MILLISECONDS.toHours(ms) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            // "0m" reads as overdue, so the last minute counts in seconds —
            // which is also the window that refreshes every tick.
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * How long to wait before recomputing. Under a minute the display changes
     * every tick, so a minute-wide gap would show a stale "0m"; above it, a
     * minute is the smallest unit on screen.
     */
    fun refreshIntervalMillis(now: Long, target: Long): Long =
        if (remainingMillis(now, target) < TimeUnit.MINUTES.toMillis(1)) 1_000L else 60_000L
}