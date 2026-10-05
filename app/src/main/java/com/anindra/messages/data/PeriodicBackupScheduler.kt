package com.anindra.messages.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the automatic plaintext backup (issue #290). The interval is kept in
 * [SettingsStore.periodicBackupInterval]; [apply] is called on every app start
 * and whenever the setting changes, so the Worker's schedule can never outlive
 * the preference that describes it.
 */
object PeriodicBackupScheduler {

    const val WORK_NAME = "messages-periodic-backup"
    const val RETRY_WORK_NAME = "messages-backup-retry"
    const val INTERVAL_DAILY = "daily"
    const val INTERVAL_WEEKLY = "weekly"
    const val DEFAULT_INTERVAL = INTERVAL_WEEKLY

    /** WorkManager's minimum backoff, enough to ride out a momentary failure. */
    const val RETRY_BACKOFF_SECONDS = 10L

    /** Days between runs. Anything other than "daily" is treated as weekly. */
    fun intervalDays(interval: String): Long =
        if (interval == INTERVAL_DAILY) 1L else 7L

    fun apply(context: Context, enabled: Boolean, interval: String) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelUniqueWork(RETRY_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<PeriodicBackupWorker>(
            intervalDays(interval), TimeUnit.DAYS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
        ).setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS
        ).build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    /**
     * A one-shot retry for a backup that already failed, so it does not wait for
     * the next period. Unique with KEEP, so repeated app starts cannot stack
     * retries on top of each other.
     */
    fun enqueueRetry(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackupRetryWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RETRY_WORK_NAME, ExistingWorkPolicy.KEEP, request
        )
    }
}
