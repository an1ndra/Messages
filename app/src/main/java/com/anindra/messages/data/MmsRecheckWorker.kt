package com.anindra.messages.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anindra.messages.sms.MmsDownloader
import com.anindra.messages.sms.MmsTrace

/**
 * Re-checks the carrier for an announced MMS that has not been fetched yet.
 *
 * Armed by [MmsRecheckScheduler] when a download cannot run (no data network) or
 * fails transiently, so the message is fetched even if the app is never opened
 * again. Retries on WorkManager's exponential backoff, capped so a device that
 * stays offline does not retry forever.
 */
class MmsRecheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val pending = runCatching {
            MmsProviderReader(app.contentResolver).pendingDownloads()
        }.getOrElse {
            MmsTrace.w(TAG, "re-check pending query failed: ${it.message}")
            emptyList()
        }
        if (pending.isEmpty()) {
            MmsTrace.i(TAG, "MMS re-check: nothing pending, stopping")
            return Result.success()
        }
        MmsTrace.i(TAG, "MMS re-check: ${pending.size} still pending")
        MmsDownloader.requestPending(app)
        return if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
            MmsTrace.i(TAG, "MMS re-check: retry budget spent, stopping")
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MmsDownload"
        const val MAX_ATTEMPTS = 6
    }
}
