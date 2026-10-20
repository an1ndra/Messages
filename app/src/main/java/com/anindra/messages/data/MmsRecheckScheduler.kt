package com.anindra.messages.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * The deferred re-check for an announced MMS that could not be fetched.
 *
 * The on-open sweep in `MmsDownloader.requestPending` only runs while the app
 * is in front, and the in-process retry is a `Handler` that dies with the
 * process. An MMS that arrives while mobile data is off — or that fails a
 * transient carrier error — would then wait until the user next opens the app.
 * This arms a background worker so it is fetched on its own.
 *
 * `NetworkType.NOT_REQUIRED`, deliberately: the app requests no network
 * permission (see `NoNetworkPermissionTest`), so the worker cannot ask "is
 * there data yet" and instead retries on an escalating backoff. WorkManager's
 * exponential backoff starts at [INITIAL_BACKOFF_MINUTES] and doubles on each
 * `Result.retry()`, so a device with data off is not polled every 15 minutes
 * forever.
 */
object MmsRecheckScheduler {

    const val WORK_NAME = "messages-mms-recheck"

    /** The first re-check delay; each retry doubles it, to WorkManager's 5-hour cap. */
    const val INITIAL_BACKOFF_MINUTES = 15L

    fun start(context: Context) {
        val request = OneTimeWorkRequestBuilder<MmsRecheckWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_MINUTES, TimeUnit.MINUTES
            )
            .build()
        // KEEP, not REPLACE: a repeated download failure must not reset the
        // backoff, or a device with data off would be re-checked every 15
        // minutes instead of backing off.
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.KEEP, request
        )
    }

    /** Dropped once nothing is pending, so no wake-up is left behind. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
