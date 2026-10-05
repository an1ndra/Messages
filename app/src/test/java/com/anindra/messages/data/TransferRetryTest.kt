package com.anindra.messages.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The retry budget is the whole reason a failed backup does not wait a full
 * interval: a couple of transient failures have to be absorbed, a permanent one
 * has to stop immediately, and no failure may loop forever.
 */
class TransferRetryTest {

    private class FakeSQLiteDatabaseLockedException : RuntimeException()

    @Test
    fun backoffDoublesAndCaps() {
        assertEquals(TransferRetry.BASE_DELAY_MS, TransferRetry.delayFor(1))
        assertEquals(TransferRetry.BASE_DELAY_MS * 2, TransferRetry.delayFor(2))
        assertEquals(TransferRetry.BASE_DELAY_MS * 4, TransferRetry.delayFor(3))
        assertEquals(TransferRetry.MAX_DELAY_MS, TransferRetry.delayFor(20))
        assertEquals(0L, TransferRetry.delayFor(0))
    }

    @Test
    fun ioAndLockedFailuresAreTransient() {
        assertTrue(TransferRetry.isTransient(IOException("storage busy")))
        assertTrue(TransferRetry.isTransient(FakeSQLiteDatabaseLockedException()))
        assertFalse(TransferRetry.isTransient(IllegalStateException("wrong pin")))
    }

    @Test
    fun aTransientFailureIsRetriedUntilItSucceeds() {
        val waits = mutableListOf<Long>()
        val outcome = TransferRetry.run(sleep = { waits += it }) { attempt ->
            if (attempt < 3) throw IOException("attempt $attempt")
            "ok"
        }
        assertTrue(outcome is TransferRetry.Result.Success)
        assertEquals("ok", (outcome as TransferRetry.Result.Success).value)
        assertEquals(3, outcome.attempts)
        assertEquals(listOf(TransferRetry.delayFor(1), TransferRetry.delayFor(2)), waits)
    }

    @Test
    fun theAttemptBudgetIsFinite() {
        var calls = 0
        val outcome = TransferRetry.run(maxAttempts = 3, sleep = {}) {
            calls++
            throw IOException("always")
        }
        assertEquals(3, calls)
        assertTrue(outcome is TransferRetry.Result.Failure)
        assertEquals(3, (outcome as TransferRetry.Result.Failure).attempts)
        assertTrue(outcome.transient)
    }

    @Test
    fun aPermanentFailureStopsOnTheFirstAttempt() {
        val waits = mutableListOf<Long>()
        val outcome = TransferRetry.run(sleep = { waits += it }) {
            throw IllegalStateException("wrong pin")
        }
        assertTrue(outcome is TransferRetry.Result.Failure)
        assertEquals(1, (outcome as TransferRetry.Result.Failure).attempts)
        assertFalse(outcome.transient)
        assertTrue("a permanent failure must not wait", waits.isEmpty())
    }

    @Test
    fun injectedFailuresAreConsumedOnce() {
        TransferRetry.injectFailures(2)
        assertTrue(TransferRetry.consumeInjectedFailure())
        assertTrue(TransferRetry.consumeInjectedFailure())
        assertFalse(TransferRetry.consumeInjectedFailure())
    }
}
