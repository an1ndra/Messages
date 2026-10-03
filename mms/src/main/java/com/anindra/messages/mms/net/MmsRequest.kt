package com.anindra.messages.mms.net

import android.telephony.SmsManager

/** What the transport outcome means for the next step. */
enum class RetryDecision {
    DONE,
    RETRY,
    FAILED,
}

/**
 * The app's retry policy, injected so the transport layer never re-derives it.
 *
 * The production implementation is a thin adapter over `MmsRetry`, which already
 * owns the `SUCCEEDED` / `AUTO_RETRY` / `MANUAL_RETRY` / `NO_RETRY` vocabulary,
 * the `MAX_AUTO_ATTEMPTS` budget and the doubling backoff. This interface
 * exists so that neither of those is duplicated here and so the tests can run
 * the full retry loop without sleeping.
 */
interface MmsRetryPolicy {
    val maxAutoAttempts: Int

    fun classify(resultCode: Int, httpStatus: Int): RetryDecision

    /** Backoff after [attempts] failed attempts. */
    fun delayFor(attempts: Int): Long
}

/** The platform result code vocabulary `MmsRetry.classify` already understands. */
enum class MmsResultCode(val code: Int) {
    OK(android.app.Activity.RESULT_OK),
    UNABLE_CONNECT_MMS(SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS),
    MMS_DISABLED_BY_CARRIER(SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER),
    IO_ERROR(SmsManager.MMS_ERROR_IO_ERROR),
    INVALID_SUBSCRIPTION_ID(SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID),
    RETRY(SmsManager.MMS_ERROR_RETRY),
    UNSPECIFIED(SmsManager.MMS_ERROR_UNSPECIFIED),
    HTTP_FAILURE(SmsManager.MMS_ERROR_HTTP_FAILURE),
    INACTIVE_SUBSCRIPTION(SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION),
}

/**
 * Maps a transport outcome onto a platform result code.
 *
 * `INVALID_APN` becomes `MMS_ERROR_MMS_DISABLED_BY_CARRIER` rather than
 * `MMS_ERROR_UNABLE_CONNECT_MMS` on purpose: a carrier with no usable MMS APN
 * will not grow one between attempts, so retrying it just burns the budget that
 * a genuine network outage needs.
 */
fun MmscFailure.toResultCode(): MmsResultCode = when (this) {
    MmscFailure.NO_NETWORK -> MmsResultCode.UNABLE_CONNECT_MMS
    MmscFailure.INVALID_APN -> MmsResultCode.MMS_DISABLED_BY_CARRIER
    MmscFailure.HTTP_FAILURE -> MmsResultCode.HTTP_FAILURE
    MmscFailure.TIMEOUT -> MmsResultCode.RETRY
    MmscFailure.TLS -> MmsResultCode.IO_ERROR
    MmscFailure.IO -> MmsResultCode.IO_ERROR
    MmscFailure.UNSPECIFIED -> MmsResultCode.UNSPECIFIED
}

/** The outcome of a send or retrieve, after however many attempts it took. */
data class MmsTransferResult(
    val bytes: ByteArray?,
    val failure: MmscFailure?,
    val resultCode: MmsResultCode,
    val httpStatus: Int,
    val attempts: Int,
    val decision: RetryDecision,
) {
    val succeeded: Boolean get() = bytes != null

    /** A 404 means the carrier has already discarded the notification. */
    val notFound: Boolean get() = httpStatus == MmscHttpClient.HTTP_NOT_FOUND
}

/**
 * Drives a transfer to the MMSC under the app's retry policy.
 *
 * Every dependency that would otherwise reach the platform — the network gate,
 * the APN table, the clock, the sleeper — is injected, so the whole retry loop
 * is exercised under plain JUnit with no sleeping and no framework objects.
 */
class MmsRequest(
    private val networkGate: MmsNetworkGate,
    private val engineFactory: HttpEngineFactory,
    private val apnResolver: ApnResolver,
    private val retryPolicy: MmsRetryPolicy,
    private val userAgent: () -> String?,
    private val uaProfileUrl: () -> String?,
    private val subscriptionId: () -> Int,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val networkTimeoutMillis: Long = DEFAULT_NETWORK_TIMEOUT_MS,
) {
    fun send(pduBytes: ByteArray, line1: String?): MmsTransferResult =
        retrying { post(pduBytes, it, line1) }

    fun retrieve(contentLocation: String): MmsTransferResult =
        retrying { retrieve(contentLocation, it) }

    private fun retrying(exchange: MmscHttpClient.(ApnProfile) -> MmscResponse): MmsTransferResult {
        val profile = apnResolver.resolve(subscriptionId())
            ?: return failed(MmscFailure.INVALID_APN, attempts = 0)
        val budget = retryPolicy.maxAutoAttempts.coerceAtLeast(1)
        var attempt = 0
        while (true) {
            attempt++
            val response = attemptOnce(exchange, profile)
            val (code, failure, status) = outcomeOf(response)
            val decision = retryPolicy.classify(code.code, status)
            if (decision != RetryDecision.RETRY || attempt >= budget) {
                return result(response, code, failure, status, attempt, decision)
            }
            sleeper(retryPolicy.delayFor(attempt))
        }
    }

    /**
     * One attempt. The lease is taken and released around each exchange, so a
     * retry re-evaluates whether an MMS network is available rather than holding
     * a dead one open for the whole backoff.
     */
    private fun attemptOnce(
        exchange: MmscHttpClient.(ApnProfile) -> MmscResponse,
        profile: ApnProfile,
    ): MmscResponse {
        val lease = networkGate.acquire(networkTimeoutMillis)
            ?: return MmscResponse.Failure(MmscFailure.NO_NETWORK, null)
        // A lease without a network is a failure too: proceeding would put the
        // request on the default network, which for MMS is the wrong one.
        val handle = lease.handle
        if (handle == null) {
            lease.close()
            return MmscResponse.Failure(MmscFailure.NO_NETWORK, null)
        }
        return lease.use {
            MmscHttpClient(engineFactory.create(handle), userAgent, uaProfileUrl).exchange(profile)
        }
    }

    private fun outcomeOf(response: MmscResponse): Triple<MmsResultCode, MmscFailure?, Int> =
        when (response) {
            is MmscResponse.Success -> Triple(MmsResultCode.OK, null, response.statusCode)
            is MmscResponse.Failure -> {
                val failure = response.kind
                Triple(failure.toResultCode(), failure, response.statusCode ?: 0)
            }
        }

    private fun result(
        response: MmscResponse,
        code: MmsResultCode,
        failure: MmscFailure?,
        status: Int,
        attempts: Int,
        decision: RetryDecision,
    ) = MmsTransferResult(
        bytes = (response as? MmscResponse.Success)?.bytes,
        failure = failure,
        resultCode = code,
        httpStatus = status,
        attempts = attempts,
        decision = decision,
    )

    private fun failed(failure: MmscFailure, attempts: Int): MmsTransferResult = MmsTransferResult(
        bytes = null,
        failure = failure,
        resultCode = failure.toResultCode(),
        httpStatus = 0,
        attempts = attempts,
        decision = retryPolicy.classify(failure.toResultCode().code, 0),
    )

    companion object {
        const val DEFAULT_NETWORK_TIMEOUT_MS = 10_000L

        /**
         * Wires the real dependencies, taking the MMSC identity from the cached
         * carrier profile so it is not re-read on every transfer.
         */
        fun create(
            networkGate: MmsNetworkGate,
            engineFactory: HttpEngineFactory,
            apnResolver: ApnResolver,
            retryPolicy: MmsRetryPolicy,
            carrierProfiles: CarrierProfileStore,
            subscriptionId: () -> Int,
            fallbackUserAgent: () -> String? = { null },
            sleeper: (Long) -> Unit = { Thread.sleep(it) },
            networkTimeoutMillis: Long = DEFAULT_NETWORK_TIMEOUT_MS,
        ): MmsRequest = MmsRequest(
            networkGate = networkGate,
            engineFactory = engineFactory,
            apnResolver = apnResolver,
            retryPolicy = retryPolicy,
            userAgent = {
                carrierProfiles.of(subscriptionId()).userAgent() ?: fallbackUserAgent()
            },
            uaProfileUrl = { carrierProfiles.of(subscriptionId()).uaProfUrl() },
            subscriptionId = subscriptionId,
            sleeper = sleeper,
            networkTimeoutMillis = networkTimeoutMillis,
        )
    }
}