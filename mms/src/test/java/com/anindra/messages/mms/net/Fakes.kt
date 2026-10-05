package com.anindra.messages.mms.net

import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * Returns programmed replies in order and records every request it was given.
 *
 * It takes no logger at all, which is the point: if a body or an MSISDN ever
 * reached a log line, it would have to come through this object's constructor or
 * one of its methods, and there is nowhere to put it.
 */
class RecordingHttpEngine(
    private vararg val replies: Any,
) : HttpEngine {
    val requests = mutableListOf<MmscHttpRequest>()

    override fun execute(request: MmscHttpRequest): MmscHttpReply {
        requests += request
        check(requests.size <= replies.size) { "no reply programmed for request #$requests.size" }
        return when (val reply = replies[requests.size - 1]) {
            is MmscHttpReply -> reply
            is Throwable -> throw reply
            else -> error("unsupported programmed reply: $reply")
        }
    }

    val lastRequest: MmscHttpRequest get() = requests.last()

    companion object {
        fun ok(body: ByteArray = byteArrayOf(1, 2, 3), statusCode: Int = 200) =
            MmscHttpReply(statusCode, body)

        fun status(code: Int) = MmscHttpReply(code, ByteArray(0))

        fun timeout() = SocketTimeoutException("connect timed out")

        fun tls() = SSLException("certificate pinned to the wrong root")

        fun io() = IOException("socket closed by peer")
    }
}

/**
 * A [MmsNetworkGate] with a scripted answer, counting acquisitions and releases.
 *
 * [handle] stands in for an `android.net.Network`, which cannot be constructed
 * under plain JUnit. A gate that returns null models the real failure mode: no
 * MMS network could be bound and nothing was substituted for it.
 */
class FakeNetworkGate(
    private val available: Boolean = true,
    private val handle: Any? = FakeNetwork,
) : MmsNetworkGate {
    var acquisitions = 0
        private set
    var releases = 0
        private set

    override fun acquire(timeoutMillis: Long): MmsNetworkLease? {
        acquisitions++
        if (!available) return null
        return object : MmsNetworkLease {
            override val handle: Any? = this@FakeNetworkGate.handle
            override fun close() {
                releases++
            }
        }
    }

    override fun close() = Unit

    object FakeNetwork
}

/** A retry policy with a fixed budget and no real sleeping. */
class FakeRetryPolicy(
    override val maxAutoAttempts: Int = 3,
    private val decision: (Int, Int) -> RetryDecision = { code, _ ->
        if (code == MmsResultCode.OK.code) RetryDecision.DONE else RetryDecision.RETRY
    },
) : MmsRetryPolicy {
    val delaysRequested = mutableListOf<Int>()

    override fun classify(resultCode: Int, httpStatus: Int): RetryDecision = decision(resultCode, httpStatus)

    override fun delayFor(attempts: Int): Long {
        delaysRequested += attempts
        return 1_000L * attempts
    }
}

/** Mirrors the app's `MmsRetry` semantics for the tests that need them. */
object AppRetrySemantics : MmsRetryPolicy {
    override val maxAutoAttempts: Int = 3

    override fun classify(resultCode: Int, httpStatus: Int): RetryDecision = when (resultCode) {
        MmsResultCode.OK.code -> RetryDecision.DONE
        MmsResultCode.UNABLE_CONNECT_MMS.code,
        MmsResultCode.IO_ERROR.code,
        MmsResultCode.RETRY.code -> RetryDecision.RETRY
        // A 404 means the carrier has already discarded the message.
        MmsResultCode.HTTP_FAILURE.code ->
            if (httpStatus == 404) RetryDecision.FAILED else RetryDecision.RETRY
        MmsResultCode.MMS_DISABLED_BY_CARRIER.code,
        MmsResultCode.INVALID_SUBSCRIPTION_ID.code,
        MmsResultCode.INACTIVE_SUBSCRIPTION.code -> RetryDecision.FAILED
        else -> RetryDecision.FAILED
    }

    /** Mirrors `MmsRetry.delayFor`: zero before the first attempt, then doubling. */
    override fun delayFor(attempts: Int): Long {
        if (attempts <= 0) return 0L
        return (30_000L shl (attempts - 1).coerceAtMost(16)).coerceAtMost(15 * 60_000L)
    }
}

class StaticApnResolver(private val profile: ApnProfile?) : ApnResolver {
    var queries = 0
        private set

    override fun resolve(subscriptionId: Int): ApnProfile? {
        queries++
        return profile
    }
}