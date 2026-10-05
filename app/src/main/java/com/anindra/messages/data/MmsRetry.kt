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
     * Only transient failures are retried, only while the attempt budget lasts,
     * and only once the backoff for [attempts] has elapsed. Downloading an
     * already-retrieved MMS is a no-op, so this is safe to run unattended.
     */
    fun shouldRetryNow(outcome: Int, attempts: Int, lastAttemptAt: Long, now: Long): Boolean {
        if (outcome != AUTO_RETRY || attempts >= MAX_AUTO_ATTEMPTS) return false
        if (lastAttemptAt <= 0L) return true
        return now - lastAttemptAt >= delayFor(attempts)
    }
}
