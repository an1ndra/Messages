package com.anindra.messages.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounded retry with exponential backoff for moving message data in and out.
 *
 * A backup that gives up on the first `IOException` loses a run that a second
 * attempt a fraction of a second later would have completed, and the periodic
 * worker then waits a whole interval before trying again. Mirrors [MmsRetry]'s
 * split between failures that can improve and those that cannot: a wrong PIN or
 * a corrupt file is permanent, so retrying only delays telling the user.
 *
 * Every attempt budget is finite. After [MAX_ATTEMPTS] the caller records the
 * failure and the normal schedule takes over, so a broken destination can never
 * turn into an endless retry loop.
 */
object TransferRetry {

    const val MAX_ATTEMPTS = 3
    const val BASE_DELAY_MS = 400L
    const val MAX_DELAY_MS = 8_000L

    /** Debug-only fault injection so a regression script can force a retry. */
    private val injectedFailures = AtomicInteger(0)

    fun injectFailures(attempts: Int) {
        injectedFailures.set(attempts.coerceAtLeast(0))
    }

    /** Consumes one injected failure, if any. Always false in normal runs. */
    fun consumeInjectedFailure(): Boolean {
        while (true) {
            val current = injectedFailures.get()
            if (current <= 0) return false
            if (injectedFailures.compareAndSet(current, current - 1)) return true
        }
    }

    /**
     * Whether [error] is worth another attempt.
     *
     * Classified by type rather than Android exception classes so the decision
     * is testable off-device and so a stubbed exception in a unit test does not
     * have to be a real platform one.
     */
    fun isTransient(error: Throwable): Boolean {
        if (error is IOException) return true
        val name = error.javaClass.name
        return name.endsWith("SQLiteDatabaseLockedException") ||
            name.endsWith("SQLiteDiskIOException")
    }

    /** Backoff before a retry that follows [failedAttempts] failed attempts. */
    fun delayFor(failedAttempts: Int): Long {
        if (failedAttempts <= 0) return 0L
        val shift = (failedAttempts - 1).coerceAtMost(16)
        return (BASE_DELAY_MS shl shift).coerceAtMost(MAX_DELAY_MS)
    }

    sealed interface Result<out T> {
        data class Success<T>(val value: T, val attempts: Int) : Result<T>
        data class Failure(
            val error: Throwable,
            val attempts: Int,
            val transient: Boolean
        ) : Result<Nothing>
    }

    /**
     * Runs [block] until it succeeds, the attempt budget runs out, or it throws
     * a permanent failure. [sleep] is injectable so the backoff schedule can be
     * tested without waiting for it.
     */
    fun <T> run(
        maxAttempts: Int = MAX_ATTEMPTS,
        sleep: (Long) -> Unit = { Thread.sleep(it) },
        block: (attempt: Int) -> T
    ): Result<T> {
        var attempt = 1
        while (true) {
            try {
                return Result.Success(block(attempt), attempt)
            } catch (t: Throwable) {
                val transient = isTransient(t)
                if (!transient || attempt >= maxAttempts) {
                    return Result.Failure(t, attempt, transient)
                }
                sleep(delayFor(attempt))
                attempt++
            }
        }
    }
}
