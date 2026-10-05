package com.anindra.messages.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.anindra.messages.MessagesApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes an automatic plaintext snapshot (issue #290). A background worker
 * cannot prompt for a backup PIN, which is why the scheduled backup is the
 * unencrypted SQLite copy from issue #292.
 */
class PeriodicBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = AutomaticBackup.run(applicationContext, runAttemptCount)
}

/**
 * A one-shot retry of a failed automatic backup, kept off the periodic cadence.
 *
 * A distinct class so the periodic job stays the only [PeriodicBackupWorker]
 * WorkManager holds; the scheduling tests read the schedule back by that class.
 */
class BackupRetryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = AutomaticBackup.run(applicationContext, runAttemptCount)
}

/**
 * The shared body of an automatic backup.
 *
 * A transient failure is retried with WorkManager's exponential backoff, so a
 * momentary storage or MediaStore error costs seconds rather than a whole
 * interval. The retries are capped and then reported as success, because
 * returning failure would cancel the periodic schedule itself.
 */
internal object AutomaticBackup {

    /** Retries on top of the first run; keeps a broken destination finite. */
    const val RETRY_LIMIT = 3

    suspend fun run(appContext: Context, runAttemptCount: Int): ListenableWorker.Result {
        val app = appContext as? MessagesApplication ?: return ListenableWorker.Result.success()
        val repository = app.repository
        val settings = repository.settings
        if (!settings.periodicBackupEnabled) return ListenableWorker.Result.success()
        // Privacy mode turns backups off entirely; skip without recording a
        // failed export on every tick.
        if (!BackupPolicy.isBackupAllowed(settings.privacyModeEnabled)) {
            return ListenableWorker.Result.success()
        }
        val result = withContext(Dispatchers.IO) {
            repository.backupDatabaseUnencrypted(appContext)
        }
        settings.lastBackupAt = System.currentTimeMillis()
        return when (result) {
            is Repository.ExportResult.Success -> {
                settings.lastBackupOk = true
                settings.lastBackupDetail = "Saved ${result.fileName}"
                ListenableWorker.Result.success()
            }
            is Repository.ExportResult.Error -> {
                settings.lastBackupOk = false
                settings.lastBackupDetail = result.message
                if (result.transient && runAttemptCount < RETRY_LIMIT) {
                    ListenableWorker.Result.retry()
                } else {
                    ListenableWorker.Result.success()
                }
            }
        }
    }
}
