package com.anindra.messages.data

import android.app.Activity
import android.telephony.SmsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsRetryTest {
    @Test
    fun classifiesTransientFailuresAsAutoRetry() {
        assertEquals(
            MmsRetry.AUTO_RETRY, MmsRetry.classify(SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS)
        )
        assertEquals(MmsRetry.AUTO_RETRY, MmsRetry.classify(SmsManager.MMS_ERROR_IO_ERROR))
        assertEquals(MmsRetry.AUTO_RETRY, MmsRetry.classify(SmsManager.MMS_ERROR_RETRY))
    }

    @Test
    fun classifiesPermanentFailuresAsNoRetry() {
        assertEquals(
            MmsRetry.NO_RETRY, MmsRetry.classify(SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER)
        )
        assertEquals(
            MmsRetry.NO_RETRY, MmsRetry.classify(SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID)
        )
        assertEquals(
            MmsRetry.NO_RETRY, MmsRetry.classify(SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION)
        )
    }

    @Test
    fun treatsHttp404AsGoneButOtherHttpErrorsAsTransient() {
        assertEquals(
            MmsRetry.NO_RETRY, MmsRetry.classify(SmsManager.MMS_ERROR_HTTP_FAILURE, 404)
        )
        assertEquals(
            MmsRetry.AUTO_RETRY, MmsRetry.classify(SmsManager.MMS_ERROR_HTTP_FAILURE, 503)
        )
        assertEquals(
            MmsRetry.AUTO_RETRY, MmsRetry.classify(SmsManager.MMS_ERROR_HTTP_FAILURE, 0)
        )
    }

    @Test
    fun reportsSuccessOnlyForResultOk() {
        assertEquals(MmsRetry.SUCCEEDED, MmsRetry.classify(Activity.RESULT_OK))
        // An unknown code must not be optimistically retried forever.
        assertEquals(MmsRetry.MANUAL_RETRY, MmsRetry.classify(SmsManager.MMS_ERROR_UNSPECIFIED))
        assertEquals(MmsRetry.MANUAL_RETRY, MmsRetry.classify(-12345))
    }

    @Test
    fun backsOffExponentiallyAndCaps() {
        assertEquals(0L, MmsRetry.delayFor(0))
        assertEquals(30_000L, MmsRetry.delayFor(1))
        assertEquals(60_000L, MmsRetry.delayFor(2))
        assertEquals(120_000L, MmsRetry.delayFor(3))
        assertEquals(MmsRetry.MAX_DELAY_MS, MmsRetry.delayFor(50))
    }

    @Test
    fun firstAttemptAlwaysRuns() {
        assertTrue(
            MmsRetry.shouldRetryNow(
                MmsRetry.AUTO_RETRY, attempts = 0, lastAttemptAt = 0L, now = 1_000L
            )
        )
    }

    @Test
    fun waitsOutTheBackoffBeforeRetrying() {
        val now = 1_700_000_000_000L
        assertFalse(
            MmsRetry.shouldRetryNow(MmsRetry.AUTO_RETRY, 1, now - 1_000L, now)
        )
        assertTrue(
            MmsRetry.shouldRetryNow(
                MmsRetry.AUTO_RETRY, 1, now - MmsRetry.delayFor(1), now
            )
        )
    }

    @Test
    fun stopsAfterTheAttemptBudget() {
        val now = 1_700_000_000_000L
        assertFalse(
            MmsRetry.shouldRetryNow(
                MmsRetry.AUTO_RETRY, MmsRetry.MAX_AUTO_ATTEMPTS, now - 10_000_000L, now
            )
        )
    }

    @Test
    fun neverRetriesNonTransientOutcomes() {
        val now = 1_700_000_000_000L
        assertFalse(MmsRetry.shouldRetryNow(MmsRetry.NO_RETRY, 0, 0L, now))
        assertFalse(MmsRetry.shouldRetryNow(MmsRetry.MANUAL_RETRY, 0, 0L, now))
        assertFalse(MmsRetry.shouldRetryNow(MmsRetry.SUCCEEDED, 0, 0L, now))
    }
}
