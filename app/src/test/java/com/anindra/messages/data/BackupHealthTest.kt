package com.anindra.messages.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Never ran" and "ran and failed" are different states. Only a real failure may
 * schedule the one-shot retry, or the first run would race the periodic job the
 * setting already controls.
 */
class BackupHealthTest {

    @Test
    fun aFailedRunNeedsARetry() {
        assertTrue(BackupHealth.isRetryDue(enabled = true, lastAttemptAt = 1_000L, lastSucceeded = false))
    }

    @Test
    fun aSuccessfulRunNeedsNothing() {
        assertFalse(BackupHealth.isRetryDue(enabled = true, lastAttemptAt = 1_000L, lastSucceeded = true))
    }

    @Test
    fun neverRunIsLeftToThePeriodicSchedule() {
        assertFalse(BackupHealth.isRetryDue(enabled = true, lastAttemptAt = 0L, lastSucceeded = false))
    }

    @Test
    fun disabledNeverRetries() {
        assertFalse(BackupHealth.isRetryDue(enabled = false, lastAttemptAt = 1_000L, lastSucceeded = false))
    }
}
