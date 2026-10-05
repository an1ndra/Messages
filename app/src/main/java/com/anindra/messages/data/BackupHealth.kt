package com.anindra.messages.data

/**
 * Whether an automatic backup that already failed needs a one-shot retry.
 *
 * "Never ran" and "ran and failed" are deliberately different: only a real
 * failure schedules the retry, so the very first run stays with the periodic
 * schedule the setting describes. The decision lives here once because the
 * worker, the app-start check and the diagnostics report all have to agree on
 * it — a report that re-derived it would eventually disagree with the job.
 */
object BackupHealth {
    fun isRetryDue(enabled: Boolean, lastAttemptAt: Long, lastSucceeded: Boolean): Boolean =
        enabled && lastAttemptAt > 0L && !lastSucceeded
}
