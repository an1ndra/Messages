package com.anindra.messages.data

import android.app.Activity
import android.telephony.SmsManager

/**
 * Failure classification and backoff for MMS transfers.
 *
 * A single flat cooldown treats a missing data network the same as a 404 from the
 * carrier: one burns retries that would have succeeded, the other retries
 * forever. This mirrors the AUTO_RETRY / MANUAL_RETRY / NO_RETRY split used by
 * GrapheneOS Messages and the platform `MmsService`.
 */
object MmsRetry {
    const val SUCCEEDED = 0
    const val AUTO_RETRY = 1
    const val MANUAL_RETRY = 2
    const val NO_RETRY = 3

    /**
     * The transfer could not run because there is no data network. Not a manual
     * retry (there is no UI to retry one) and not the same as a transient error
     * the carrier reported: this one clears itself the moment the user turns
     * mobile data back on, so it must not spend the automatic budget.
     */
    const val WAIT_FOR_DATA = 4

    const val MAX_AUTO_ATTEMPTS = 3
    const val BASE_DELAY_MS = 30_000L
    const val MAX_DELAY_MS = 15 * 60_000L
    const val HTTP_NOT_FOUND = 404

    fun classify(resultCode: Int, httpStatus: Int = 0): Int = when (resultCode) {
        Activity.RESULT_OK -> SUCCEEDED
        SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS,
        SmsManager.MMS_ERROR_IO_ERROR,
        SmsManager.MMS_ERROR_RETRY -> AUTO_RETRY
        // A 404 means the carrier has already discarded the message; every other
        // HTTP failure is worth another attempt.
        SmsManager.MMS_ERROR_HTTP_FAILURE ->
            if (httpStatus == HTTP_NOT_FOUND) NO_RETRY else AUTO_RETRY
        // Data is off: a message that arrived now is not lost, it is simply not
        // fetchable yet. Treating it as a manual retry dropped the state and
        // left the row to whatever happened to sweep next.
        SmsManager.MMS_ERROR_NO_DATA_NETWORK,
        SmsManager.MMS_ERROR_DATA_DISABLED -> WAIT_FOR_DATA
        SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER,
        SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID,
        SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION -> NO_RETRY
        else -> MANUAL_RETRY
    }

    /** Backoff for [attempts] already made, doubling and capped at [MAX_DELAY_MS]. */
    fun delayFor(attempts: Int): Long {
        if (attempts <= 0) return 0L
        val shift = (attempts - 1).coerceAtMost(16)
        return (BASE_DELAY_MS shl shift).coerceAtMost(MAX_DELAY_MS)
    }

    /**
     * Only transient failures and the no-data case are retried. Past the attempt
     * budget a transient failure is not abandoned — it is retried on the longest
     * cooldown, so a condition that later clears is still picked up. Before, the
     * map entry stayed at the limit and the row was never fetched again until
     * the process died.
     */
    fun shouldRetryNow(outcome: Int, attempts: Int, lastAttemptAt: Long, now: Long): Boolean {
        if (outcome != AUTO_RETRY && outcome != WAIT_FOR_DATA) return false
        if (lastAttemptAt <= 0L) return true
        val delay = if (outcome == AUTO_RETRY && attempts >= MAX_AUTO_ATTEMPTS) {
            MAX_DELAY_MS
        } else {
            delayFor(attempts)
        }
        return now - lastAttemptAt >= delay
    }
}
