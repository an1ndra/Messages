package com.anindra.messages.mms.net

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** One pending retry, as the queue sees it. */
data class MmsRetryJob(val messageId: Long, val attempt: Int, val delayMillis: Long)

/**
 * Where a scheduled retry is handed off to.
 *
 * The point of this seam is that the reference implementation's retry path is a
 * literal no-op, so an in-flight MMS dies with the process. Whatever backs this
 * has to be durable.
 */
interface MmsWorkQueue {
    fun enqueue(job: MmsRetryJob)
    fun cancel(messageId: Long)
}

/** Builds the worker that performs the retry. Supplied by the app. */
fun interface MmsRetryWorkerFactory {
    fun create(messageId: Long, attempt: Int): androidx.work.ListenableWorker?
}

class MmsRetryScheduler(
    private val queue: MmsWorkQueue,
    private val workerFactory: MmsRetryWorkerFactory,
) {
    fun schedule(messageId: Long, attempt: Int, delayMillis: Long) =
        queue.enqueue(MmsRetryJob(messageId, attempt, delayMillis))

    fun cancel(messageId: Long) = queue.cancel(messageId)

    companion object {
        const val WORK_NAME_PREFIX = "mms-retry-"

        fun workName(messageId: Long): String = "$WORK_NAME_PREFIX$messageId"
    }
}

/**
 * WorkManager-backed queue, so a retry outlives the process.
 *
 * The work is unique per message id and replaces any earlier attempt: a message
 * has exactly one retry pending at a time, and letting two run would send it
 * twice.
 */
class WorkManagerMmsWorkQueue(
    context: Context,
    private val workerFactory: MmsRetryWorkerFactory,
) : MmsWorkQueue {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun enqueue(job: MmsRetryJob) {
        val worker = workerFactory.create(job.messageId, job.attempt) ?: return
        val request = OneTimeWorkRequest.Builder(worker.javaClass)
            .setInitialDelay(job.delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    KEY_MESSAGE_ID to job.messageId,
                    KEY_ATTEMPT to job.attempt,
                )
            )
            .addTag(tagFor(job.messageId))
            .build()
        workManager.enqueueUniqueWork(
            MmsRetryScheduler.workName(job.messageId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel(messageId: Long) {
        workManager.cancelUniqueWork(MmsRetryScheduler.workName(messageId))
    }

    companion object {
        const val KEY_MESSAGE_ID = "messageId"
        const val KEY_ATTEMPT = "attempt"

        fun tagFor(messageId: Long): String = MmsRetryScheduler.workName(messageId)
    }
}