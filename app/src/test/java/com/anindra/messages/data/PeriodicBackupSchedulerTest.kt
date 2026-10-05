package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #290: the scheduled backup maps the stored interval to a WorkManager period,
 * and — because a background worker cannot prompt for a backup PIN — it writes
 * the plaintext snapshot rather than an encrypted one.
 */
class PeriodicBackupSchedulerTest {

    private val workerSource: String by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/anindra/messages/data/PeriodicBackupWorker.kt") }
            .firstOrNull { it.isFile }
            ?: error("PeriodicBackupWorker.kt not found")
        file.readText()
    }

    private val schedulerSource: String by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/anindra/messages/data/PeriodicBackupScheduler.kt") }
            .firstOrNull { it.isFile }
            ?: error("PeriodicBackupScheduler.kt not found")
        file.readText()
    }

    @Test
    fun dailyIsOneDay() {
        assertEquals(1L, PeriodicBackupScheduler.intervalDays(PeriodicBackupScheduler.INTERVAL_DAILY))
    }

    @Test
    fun weeklyIsSevenDays() {
        assertEquals(7L, PeriodicBackupScheduler.intervalDays(PeriodicBackupScheduler.INTERVAL_WEEKLY))
    }

    @Test
    fun anUnknownIntervalAndTheDefaultBothMeanWeekly() {
        assertEquals(PeriodicBackupScheduler.INTERVAL_WEEKLY, PeriodicBackupScheduler.DEFAULT_INTERVAL)
        assertEquals(7L, PeriodicBackupScheduler.intervalDays("hourly"))
    }

    @Test
    fun theWorkerWritesPlaintextAndSkipsWhenTheSettingOrPrivacyModeSaysSo() {
        assertTrue(
            "the scheduled backup must use the unencrypted export; a Worker cannot prompt for a PIN",
            workerSource.contains("backupDatabaseUnencrypted")
        )
        assertTrue(
            "the worker must bail out when the setting is off",
            workerSource.contains("periodicBackupEnabled")
        )
        assertTrue(
            "the worker must respect privacy mode instead of recording a failed export every tick",
            workerSource.contains("isBackupAllowed")
        )
    }

    @Test
    fun aTransientFailureIsRetriedWithBackoffButCapped() {
        assertTrue(
            "the worker must ask WorkManager to retry a transient failure",
            workerSource.contains("Result.retry()")
        )
        assertTrue(
            "the retry has to be capped, or a broken destination loops forever",
            workerSource.contains("RETRY_LIMIT")
        )
        assertTrue(
            "a non-transient failure must not be retried",
            workerSource.contains("result.transient")
        )
        assertTrue(
            "the worker records the outcome so startup retry and diagnostics can read it",
            workerSource.contains("lastBackupOk")
        )
    }

    @Test
    fun bothThePeriodicAndRetryJobsUseExponentialBackoff() {
        assertTrue(
            "the periodic job must configure exponential backoff",
            schedulerSource.contains("BackoffPolicy.EXPONENTIAL")
        )
        assertTrue(
            "a failed backup needs a one-shot retry independent of the period",
            schedulerSource.contains("enqueueUniqueWork")
        )
        assertTrue(
            "the retry must be unique so repeated app starts cannot stack it",
            schedulerSource.contains("ExistingWorkPolicy.KEEP")
        )
    }
}
