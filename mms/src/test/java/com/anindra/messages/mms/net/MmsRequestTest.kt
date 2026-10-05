package com.anindra.messages.mms.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal val PROFILED_APN = ApnProfile(
    apnName = "mms",
    mmscUrl = "http://mms.example.net/servlets/mms",
    mmsProxy = null,
    mmsPort = null,
    type = "mms",
)

/**
 * The retry loop, driven against an injected policy so nothing sleeps and no
 * framework object is involved.
 */
class MmsRequestTest {
    private val profile = PROFILED_APN

    private val pdu = byteArrayOf(0x0B, 0x80.toByte(), 0x00, 0x01, 0x02)

    private class Harness(
        engine: RecordingHttpEngine,
        gate: MmsNetworkGate = FakeNetworkGate(),
        apn: ApnProfile? = PROFILED_APN,
        policy: MmsRetryPolicy = FakeRetryPolicy(),
    ) {
        val delays = mutableListOf<Long>()
        val handles = mutableListOf<Any?>()
        val request = MmsRequest(
            networkGate = gate,
            engineFactory = { handle ->
                handles += handle
                engine
            },
            apnResolver = StaticApnResolver(apn),
            retryPolicy = policy,
            userAgent = { "TestAgent/1.0" },
            uaProfileUrl = { null },
            subscriptionId = { 3 },
            sleeper = { delays += it },
            networkTimeoutMillis = 1_000L,
        )
    }

    @Test
    fun aSuccessfulSendReturnsThePduReplyOnTheFirstAttempt() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(byteArrayOf(9, 9, 9)))
        val harness = Harness(engine)

        val result = harness.request.send(pdu, line1 = null)

        assertTrue(result.succeeded)
        assertArrayEquals(byteArrayOf(9, 9, 9), result.bytes)
        assertEquals(1, result.attempts)
        assertEquals(RetryDecision.DONE, result.decision)
        assertEquals(MmsResultCode.OK, result.resultCode)
        assertArrayEquals(pdu, engine.lastRequest.body)
        assertTrue("no backoff on success", harness.delays.isEmpty())
    }

    @Test
    fun retriesStopAtThePolicysAttemptBudget() {
        val engine = RecordingHttpEngine(
            RecordingHttpEngine.io(),
            RecordingHttpEngine.io(),
            RecordingHttpEngine.io(),
            RecordingHttpEngine.io(),
        )
        val harness = Harness(engine, policy = FakeRetryPolicy(maxAutoAttempts = 3))

        val result = harness.request.send(pdu, line1 = null)

        assertFalse(result.succeeded)
        assertEquals(3, result.attempts)
        assertEquals(3, engine.requests.size)
        assertEquals("the fourth reply must never be consumed", 3, engine.requests.size)
        assertEquals(RetryDecision.RETRY, result.decision)
    }

    @Test
    fun theBackoffDelaysComeFromTheInjectedPolicy() {
        val engine = RecordingHttpEngine(
            RecordingHttpEngine.io(),
            RecordingHttpEngine.io(),
            RecordingHttpEngine.io(),
        )
        val harness = Harness(engine, policy = FakeRetryPolicy(maxAutoAttempts = 3))

        harness.request.send(pdu, line1 = null)

        // FakeRetryPolicy returns 1_000 * attempts and records the attempt index.
        assertEquals(listOf(1_000L, 2_000L), harness.delays)
    }

    @Test
    fun anAppPolicysDoublingBackoffIsHonouredAcrossTheBudget() {
        val engine = RecordingHttpEngine(
            RecordingHttpEngine.io(),
            RecordingHttpEngine.io(),
            RecordingHttpEngine.io(),
        )
        val policy = AppRetrySemantics
        val harness = Harness(engine, policy = policy)

        harness.request.send(pdu, line1 = null)

        assertEquals(
            listOf(policy.delayFor(1), policy.delayFor(2)),
            harness.delays,
        )
    }

    @Test
    fun aRecoveringTransferSucceedsOnALaterAttempt() {
        val engine = RecordingHttpEngine(
            RecordingHttpEngine.io(),
            RecordingHttpEngine.ok(byteArrayOf(7)),
        )
        val harness = Harness(engine)

        val result = harness.request.send(pdu, line1 = null)

        assertTrue(result.succeeded)
        assertEquals(2, result.attempts)
        assertEquals(listOf(1_000L), harness.delays)
    }

    @Test
    fun aNotFoundIsNotRetriedSoTheNotificationCanBeDropped() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.status(404))
        val harness = Harness(engine, policy = AppRetrySemantics)

        val result = harness.request.retrieve("http://mms.example.net/inbox/1")

        assertFalse(result.succeeded)
        assertEquals(1, result.attempts)
        assertTrue(result.notFound)
        assertEquals(RetryDecision.FAILED, result.decision)
        assertEquals("one request only", 1, engine.requests.size)
    }

    @Test
    fun aServerErrorIsRetried() {
        val engine = RecordingHttpEngine(
            RecordingHttpEngine.status(503),
            RecordingHttpEngine.status(503),
            RecordingHttpEngine.status(503),
        )
        val harness = Harness(engine, policy = AppRetrySemantics)

        val result = harness.request.retrieve("http://mms.example.net/inbox/1")

        assertEquals(3, result.attempts)
        assertEquals(503, result.httpStatus)
        assertTrue(result.notFound.not())
    }

    @Test
    fun noMmsNetworkYieldsNoNetworkAndIsNeverSubstituted() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())
        val harness = Harness(engine, gate = FakeNetworkGate(available = false))

        val result = harness.request.send(pdu, line1 = null)

        assertEquals(MmscFailure.NO_NETWORK, result.failure)
        assertEquals(MmsResultCode.UNABLE_CONNECT_MMS, result.resultCode)
        assertTrue("nothing may be sent without a network", engine.requests.isEmpty())
        assertTrue("no engine may be built without a network", harness.handles.isEmpty())
    }

    @Test
    fun noMmsNetworkConsumesTheWholeBudgetThenGivesUp() {
        val gate = FakeNetworkGate(available = false)
        val harness = Harness(RecordingHttpEngine(), gate = gate, policy = AppRetrySemantics)

        val result = harness.request.send(pdu, line1 = null)

        assertEquals(3, result.attempts)
        assertEquals(3, gate.acquisitions)
        assertEquals(0, gate.releases)
        assertFalse(result.succeeded)
    }

    @Test
    fun aLeaseWithoutANetworkIsRefusedRatherThanRidingTheDefaultOne() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())
        val gate = FakeNetworkGate(handle = null)
        val harness = Harness(engine, gate = gate)

        val result = harness.request.send(pdu, line1 = null)

        assertEquals(MmscFailure.NO_NETWORK, result.failure)
        assertTrue("no engine may be built from an unbound lease", harness.handles.isEmpty())
        assertTrue(engine.requests.isEmpty())
        assertEquals("every unbound lease is still released", 3, gate.releases)
    }

    @Test
    fun everyLeaseIsReleasedAfterEachAttempt() {
        val engine = RecordingHttpEngine(
            RecordingHttpEngine.io(),
            RecordingHttpEngine.io(),
            RecordingHttpEngine.ok(),
        )
        val gate = FakeNetworkGate()
        val harness = Harness(engine, gate = gate)

        harness.request.send(pdu, line1 = null)

        assertEquals(3, gate.acquisitions)
        assertEquals("a held network is not left open across a backoff", 3, gate.releases)
    }

    @Test
    fun eachAttemptIsGivenTheHandleFromItsOwnLease() {
        val engine = RecordingHttpEngine(
            RecordingHttpEngine.io(),
            RecordingHttpEngine.ok(),
        )
        val harness = Harness(engine)

        harness.request.send(pdu, line1 = null)

        assertEquals(listOf<Any?>(FakeNetworkGate.FakeNetwork, FakeNetworkGate.FakeNetwork), harness.handles)
    }

    @Test
    fun anApnWithNoMmscNeverReachesTheTransport() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())
        val harness = Harness(engine, apn = ApnProfile("data", "", null, null, "default"))

        val result = harness.request.send(pdu, line1 = null)

        assertEquals(MmscFailure.INVALID_APN, result.failure)
        assertEquals(MmsResultCode.MMS_DISABLED_BY_CARRIER, result.resultCode)
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun aMissingApnIsNotRetriedBecauseTheCarrierWillNotGrowOne() {
        val gate = FakeNetworkGate()
        val harness = Harness(RecordingHttpEngine(), gate = gate, apn = null, policy = AppRetrySemantics)

        val result = harness.request.send(pdu, line1 = null)

        assertEquals(MmscFailure.INVALID_APN, result.failure)
        assertEquals(0, result.attempts)
        assertEquals(RetryDecision.FAILED, result.decision)
        assertEquals("not even the network is requested", 0, gate.acquisitions)
    }

    @Test
    fun aTimeoutIsClassifiedAsATimeoutThroughTheWholeLoop() {
        val engine = RecordingHttpEngine(
            RecordingHttpEngine.timeout(),
            RecordingHttpEngine.timeout(),
            RecordingHttpEngine.timeout(),
        )
        val harness = Harness(engine, policy = AppRetrySemantics)

        val result = harness.request.send(pdu, line1 = null)

        assertEquals(MmscFailure.TIMEOUT, result.failure)
        assertEquals(3, result.attempts)
    }

    @Test
    fun aTlsFailureIsClassifiedAsTlsAndRetried() {
        val engine = RecordingHttpEngine(
            RecordingHttpEngine.tls(),
            RecordingHttpEngine.tls(),
            RecordingHttpEngine.tls(),
        )
        val harness = Harness(engine, policy = AppRetrySemantics)

        val result = harness.request.send(pdu, line1 = null)

        assertEquals(MmscFailure.TLS, result.failure)
        assertEquals(MmsResultCode.IO_ERROR, result.resultCode)
        assertEquals(3, result.attempts)
    }

    @Test
    fun theApnIsResolvedOnceForTheWholeLoopRatherThanPerAttempt() {
        val engine = RecordingHttpEngine(
            RecordingHttpEngine.io(),
            RecordingHttpEngine.io(),
            RecordingHttpEngine.ok(),
        )
        val resolver = StaticApnResolver(profile)
        val request = MmsRequest(
            networkGate = FakeNetworkGate(),
            engineFactory = { engine },
            apnResolver = resolver,
            retryPolicy = FakeRetryPolicy(),
            userAgent = { null },
            uaProfileUrl = { null },
            subscriptionId = { 7 },
            sleeper = { },
        )

        request.send(pdu, line1 = null)

        assertEquals(1, resolver.queries)
    }

    @Test
    fun theApnIsResolvedForTheSubscriptionTheCallerNamed() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())
        val resolver = StaticApnResolver(profile)
        val seen = mutableListOf<Int>()
        val request = MmsRequest(
            networkGate = FakeNetworkGate(),
            engineFactory = { engine },
            apnResolver = object : ApnResolver {
                override fun resolve(subscriptionId: Int): ApnProfile? {
                    seen += subscriptionId
                    return resolver.resolve(subscriptionId)
                }
            },
            retryPolicy = FakeRetryPolicy(),
            userAgent = { null },
            uaProfileUrl = { null },
            subscriptionId = { 42 },
            sleeper = { },
        )

        request.retrieve("http://mms.example.net/inbox/1")

        assertEquals(listOf(42), seen)
    }

    @Test
    fun aRetrieveSendsNoBody() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())
        val harness = Harness(engine)

        harness.request.retrieve("http://mms.example.net/inbox/1")

        assertEquals("GET", engine.lastRequest.method)
        assertNull(engine.lastRequest.body)
    }

    @Test
    fun theWiringInReadsTheMmscIdentityFromTheCarrierProfile() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())
        val profiles = CarrierProfileStore(
            MapCarrierValues(emptyMap()),
            CarrierConfigSource {
                MapCarrierValues(
                    mapOf(
                        CarrierProfile.KEY_USER_AGENT to "CarrierAgent/2.0",
                        CarrierProfile.KEY_UA_PROFILE_URL to "http://carrier.example/##LINE1##",
                    )
                )
            },
            { -1 },
        )
        val request = MmsRequest.create(
            networkGate = FakeNetworkGate(),
            engineFactory = { engine },
            apnResolver = StaticApnResolver(PROFILED_APN),
            retryPolicy = FakeRetryPolicy(),
            carrierProfiles = profiles,
            subscriptionId = { 5 },
            sleeper = {},
        )

        request.send(pdu, line1 = "+447700900123")

        assertEquals("CarrierAgent/2.0", engine.lastRequest.headers["User-Agent"])
        assertEquals("http://carrier.example/+447700900123", engine.lastRequest.headers["x-wap-profile"])
    }

    @Test
    fun theWiringInFallsBackWhenTheProfileHasNoUserAgent() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok())
        val profiles = CarrierProfileStore(MapCarrierValues(emptyMap()), CarrierConfigSource { null }, { -1 })
        val request = MmsRequest.create(
            networkGate = FakeNetworkGate(),
            engineFactory = { engine },
            apnResolver = StaticApnResolver(PROFILED_APN),
            retryPolicy = FakeRetryPolicy(),
            carrierProfiles = profiles,
            subscriptionId = { 5 },
            fallbackUserAgent = { "TelephonyManagerAgent/1.0" },
            sleeper = {},
        )

        request.send(pdu, line1 = null)

        assertEquals("TelephonyManagerAgent/1.0", engine.lastRequest.headers["User-Agent"])
    }

    @Test
    fun aBudgetOfOneMeansExactlyOneAttempt() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.io(), RecordingHttpEngine.ok())
        val harness = Harness(engine, policy = FakeRetryPolicy(maxAutoAttempts = 1))

        val result = harness.request.send(pdu, line1 = null)

        assertEquals(1, result.attempts)
        assertEquals(1, engine.requests.size)
        assertTrue(harness.delays.isEmpty())
    }
}