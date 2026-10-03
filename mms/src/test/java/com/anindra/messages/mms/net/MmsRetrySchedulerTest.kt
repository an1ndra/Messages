package com.anindra.messages.mms.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A [MmsWorkQueue] that records what it was handed. */
private class RecordingWorkQueue : MmsWorkQueue {
    val enqueued = mutableListOf<MmsRetryJob>()
    val cancelled = mutableListOf<Long>()

    override fun enqueue(job: MmsRetryJob) {
        enqueued += job
    }

    override fun cancel(messageId: Long) {
        cancelled += messageId
    }
}

/**
 * The scheduler has to survive process death, which is the whole reason it is
 * WorkManager-backed: the reference implementation's retry path is a literal
 * no-op, so an in-flight MMS dies with the process.
 */
class MmsRetrySchedulerTest {
    private val queue = RecordingWorkQueue()
    private val scheduler = MmsRetryScheduler(queue, workerFactory = { _, _ -> null })

    @Test
    fun aScheduledRetryCarriesTheMessageAttemptAndDelay() {
        scheduler.schedule(messageId = 17L, attempt = 2, delayMillis = 45_000L)

        assertEquals(listOf(MmsRetryJob(17L, 2, 45_000L)), queue.enqueued)
    }

    @Test
    fun theDelayComesFromTheBackoffRatherThanBeingInvented() {
        val policy = AppRetrySemantics

        scheduler.schedule(messageId = 1L, attempt = 3, delayMillis = policy.delayFor(3))

        assertEquals(policy.delayFor(3), queue.enqueued.single().delayMillis)
        assertEquals(120_000L, queue.enqueued.single().delayMillis)
    }

    @Test
    fun everyMessageGetsItsOwnUniqueWorkName() {
        assertEquals("mms-retry-17", MmsRetryScheduler.workName(17L))
        assertEquals("mms-retry-18", MmsRetryScheduler.workName(18L))
    }

    @Test
    fun cancellingTargetsTheSameUniqueWorkName() {
        scheduler.schedule(messageId = 17L, attempt = 1, delayMillis = 30_000L)
        scheduler.cancel(messageId = 17L)

        assertEquals(listOf(17L), queue.cancelled)
        assertEquals(MmsRetryScheduler.workName(17L), WorkManagerMmsWorkQueue.tagFor(17L))
    }

    @Test
    fun reschedulingReplacesRatherThanStackingBecauseTheNameIsUnique() {
        scheduler.schedule(messageId = 17L, attempt = 1, delayMillis = 30_000L)
        scheduler.schedule(messageId = 17L, attempt = 2, delayMillis = 60_000L)

        assertEquals("one message, one pending retry", 2, queue.enqueued.size)
        assertEquals(MmsRetryScheduler.workName(17L), MmsRetryScheduler.workName(17L))
    }

    @Test
    fun cancellingAMessageWithNoRetryScheduledIsHarmless() {
        scheduler.cancel(messageId = 999L)

        assertEquals(listOf(999L), queue.cancelled)
    }

    @Test
    fun theWorkerInputKeysAreStable() {
        assertEquals("messageId", WorkManagerMmsWorkQueue.KEY_MESSAGE_ID)
        assertEquals("attempt", WorkManagerMmsWorkQueue.KEY_ATTEMPT)
    }

    @Test
    fun theAppRetrySemanticsMatchTheAppsBudget() {
        // MmsRetry.MAX_AUTO_ATTEMPTS in the app module. Duplicated here as a
        // literal only because the test cannot see across modules; if either
        // side changes, this test is the place that has to be updated.
        assertEquals(3, AppRetrySemantics.maxAutoAttempts)
        assertEquals(30_000L, AppRetrySemantics.delayFor(1))
        assertEquals(120_000L, AppRetrySemantics.delayFor(3))
        assertEquals(60_000L, AppRetrySemantics.delayFor(2))
        assertEquals(15 * 60_000L, AppRetrySemantics.delayFor(20))
        assertEquals(0L, AppRetrySemantics.delayFor(0))
    }

    @Test
    fun aNoNetworkOutcomeIsRetriedAndA404IsNot() {
        assertEquals(
            RetryDecision.RETRY,
            AppRetrySemantics.classify(MmsResultCode.UNABLE_CONNECT_MMS.code, 0),
        )
        assertEquals(
            RetryDecision.FAILED,
            AppRetrySemantics.classify(MmsResultCode.HTTP_FAILURE.code, 404),
        )
        assertEquals(
            RetryDecision.RETRY,
            AppRetrySemantics.classify(MmsResultCode.HTTP_FAILURE.code, 500),
        )
        assertEquals(RetryDecision.DONE, AppRetrySemantics.classify(MmsResultCode.OK.code, 200))
    }

    @Test
    fun everyTransportFailureMapsToAPlatformResultCode() {
        assertEquals(MmsResultCode.UNABLE_CONNECT_MMS, MmscFailure.NO_NETWORK.toResultCode())
        assertEquals(MmsResultCode.MMS_DISABLED_BY_CARRIER, MmscFailure.INVALID_APN.toResultCode())
        assertEquals(MmsResultCode.HTTP_FAILURE, MmscFailure.HTTP_FAILURE.toResultCode())
        assertEquals(MmsResultCode.RETRY, MmscFailure.TIMEOUT.toResultCode())
        assertEquals(MmsResultCode.IO_ERROR, MmscFailure.TLS.toResultCode())
        assertEquals(MmsResultCode.IO_ERROR, MmscFailure.IO.toResultCode())
        assertEquals(MmsResultCode.UNSPECIFIED, MmscFailure.UNSPECIFIED.toResultCode())
    }

    @Test
    fun theResultCodesAreDistinctSoClassificationCannotCollide() {
        val codes = MmsResultCode.entries.map { it.code }

        assertEquals("two result codes share a value", codes.size, codes.toSet().size)
    }
}

