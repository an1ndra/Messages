package com.anindra.messages.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The deferred MMS re-check (issue #328 follow-up): an announced message that
 * cannot be fetched because mobile data is off must still be fetched once data
 * returns, even if the app is never opened again.
 *
 * A WorkManager worker and its scheduler cannot be driven from plain JUnit, so
 * this reads the source the way the other scheduling tests do: it pins the
 * decisions that make the re-check correct — permission-free, backoff that is
 * not reset by every failure, a bounded retry, and a stop when nothing is left.
 */
class MmsRecheckSchedulerTest {

    private val schedulerSource: String by lazy {
        read("app/src/main/java/com/anindra/messages/data/MmsRecheckScheduler.kt")
    }
    private val workerSource: String by lazy {
        read("app/src/main/java/com/anindra/messages/data/MmsRecheckWorker.kt")
    }
    private val downloaderSource: String by lazy {
        read("app/src/main/java/com/anindra/messages/sms/MmsDownloader.kt")
    }

    @Test
    fun theRecheckRequestsNoNetworkPermission() {
        assertTrue(
            "the re-check must not need a network constraint, or the app would have to " +
                "re-add ACCESS_NETWORK_STATE and reverse #332",
            schedulerSource.contains("NetworkType.NOT_REQUIRED")
        )
    }

    @Test
    fun repeatedFailuresDoNotResetTheBackoff() {
        assertTrue(
            "the worker must back off exponentially",
            schedulerSource.contains("BackoffPolicy.EXPONENTIAL")
        )
        assertTrue(
            "a repeated failure must keep the existing re-check, not restart the backoff",
            schedulerSource.contains("ExistingWorkPolicy.KEEP")
        )
    }

    @Test
    fun theRetryIsBoundedAndStopsWhenNothingIsPending() {
        assertTrue(
            "the worker must re-request the pending sweep",
            workerSource.contains("MmsDownloader.requestPending")
        )
        assertTrue(
            "the worker must ask WorkManager to retry",
            workerSource.contains("Result.retry()")
        )
        assertTrue(
            "an unbounded retry would poll a device that stays offline forever",
            workerSource.contains("MAX_ATTEMPTS")
        )
        assertTrue(
            "with nothing pending the re-check must stop, not reschedule",
            workerSource.contains("pending.isEmpty()")
        )
    }

    @Test
    fun aNoDataFailurePreservesTheBudgetAndArmsTheRecheck() {
        assertTrue(
            "the no-data outcome must be handled in onComplete",
            downloaderSource.contains("MmsRetry.WAIT_FOR_DATA")
        )
        assertTrue(
            "a no-data failure must give the attempt back rather than spend it",
            downloaderSource.contains("attempt.count = (attempt.count - 1)")
        )
        assertTrue(
            "a no-data or transient failure must arm the deferred re-check",
            downloaderSource.contains("MmsRecheckScheduler.start")
        )
    }

    private fun read(rel: String): String {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, rel) }
            .firstOrNull { it.isFile }
            ?: error("$rel not found")
        return file.readText()
    }
}
