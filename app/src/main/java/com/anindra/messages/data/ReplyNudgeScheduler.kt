package com.anindra.messages.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anindra.messages.MessagesApplication
import com.anindra.messages.sms.ReplyNudge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Arms and disarms the unanswered-reply nudge (#484).
 *
 * `apply` runs on every app start and whenever the setting changes, so the
 * Worker's schedule can never outlive the preference that describes it. The
 * periodic job is WorkManager's minimum period: it only asks a cheap question
 * ("is anything due?") and does nothing at all unless a thread has been
 * unanswered for a day or more.
 */
object ReplyNudgeScheduler {

    const val WORK_NAME = "messages-reply-nudge"
    const val CHECK_WORK_NAME = "messages-reply-nudge-check"

    /** WorkManager's floor for periodic work; the policy decides what is due. */
    const val PERIOD_MINUTES = 15L

    fun apply(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelUniqueWork(CHECK_WORK_NAME)
            // A reminder that is already in the shade has to go with the
            // switch; no worker is left to reconcile it.
            ReplyNudge.cancelAll(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReplyNudgeWorker>(
            PERIOD_MINUTES, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
        ).build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    /**
     * One check outside the period: switching the feature on, and every app
     * start, reconcile immediately instead of waiting for the next tick. KEEP,
     * so a burst of starts cannot stack checks.
     */
    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReplyNudgeWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
            ).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHECK_WORK_NAME, ExistingWorkPolicy.KEEP, request
        )
    }
}

/**
 * Asks [ReplyNudge] to reconcile the shade. Safe to run at any time: the policy
 * is the only thing that decides, and it does not care how it was woken.
 */
class ReplyNudgeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MessagesApplication ?: return Result.success()
        if (!app.repository.settings.replyNudgeEnabled) return Result.success()
        withContext(Dispatchers.IO) { ReplyNudge.reconcile(applicationContext) }
        return Result.success()
    }
}
