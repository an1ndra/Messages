package com.anindra.messages.mms.transport

import com.anindra.messages.mms.Wire
import com.anindra.messages.mms.net.ApnProfile
import com.anindra.messages.mms.net.CarrierProfile
import com.anindra.messages.mms.net.CarrierProfileStore
import com.anindra.messages.mms.net.FakeNetworkGate
import com.anindra.messages.mms.net.HttpEngine
import com.anindra.messages.mms.net.HttpEngineFactory
import com.anindra.messages.mms.net.MmsResultCode
import com.anindra.messages.mms.net.MmscHttpClient
import com.anindra.messages.mms.net.RecordingHttpEngine
import com.anindra.messages.mms.net.StaticApnResolver
import com.anindra.messages.mms.pdu.EncodedStringValue
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduBody
import com.anindra.messages.mms.pdu.PduComposer
import com.anindra.messages.mms.pdu.PduPart
import com.anindra.messages.mms.pdu.PduParser
import com.anindra.messages.mms.profileStoreOf
import com.anindra.messages.mms.spi.TransportListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

/**
 * The direct path's claims that matter are that a missing network is a named
 * failure rather than a silent substitution onto something else, and that every
 * failure it reports is a platform result code so the retry policy above it never
 * has to know which transport ran.
 */
class DirectMmscTransportTest {

    private val inline = Executor { it.run() }
    private val directApn = ApnProfile("mms", "http://mmsc.test/servlets/mms", null, null, "mms")
    private val proxiedApn = ApnProfile("mms", "http://mmsc.test/servlets/mms", "proxy.test", 8080, "mms")
    private val ownNumber = "+15550001111"

    private fun transport(
        engine: RecordingHttpEngine,
        apn: ApnProfile? = directApn,
        gate: FakeNetworkGate = FakeNetworkGate(),
        profiles: CarrierProfileStore = profileStoreOf(),
        line1: () -> String? = { ownNumber },
    ): Pair<DirectMmscTransport, CountingEngineFactory> {
        val factory = CountingEngineFactory(engine)
        return DirectMmscTransport(
            apnResolver = StaticApnResolver(apn),
            networkGate = gate,
            engineFactory = factory,
            carrierProfiles = profiles,
            line1 = { line1() },
            workers = inline,
        ) to factory
    }

    private val sendReq = Pdu(MessageType.SEND_REQ).apply {
        headers.setContentType("application/vnd.wap.multipart.related")
        headers.setText(HeaderField.TRANSACTION_ID, "T-send")
        headers.addEncoded(HeaderField.TO, EncodedStringValue.utf8("+15559998888"))
        from = EncodedStringValue.insertAddressToken()
        body = PduBody().also { body ->
            body.add(
                PduPart().apply {
                    contentType = "text/plain"
                    name = "text"
                    data = "body".toByteArray(Charsets.UTF_8)
                },
            )
        }
    }

    @Test
    fun theTransportReportsTheChoiceItIsRegisteredUnder() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        assertEquals(TransportChoice.DIRECT.id, transport(engine).first.id)
    }

    @Test
    fun aProxylessApnIsReachedDirectly() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        val listener = RecordingTransportListener()
        assertTrue(transport(engine).first.send(sendReq, Wire.SUBSCRIPTION_ID, listener))

        assertNull(engine.lastRequest.proxy)
        assertEquals(MmscHttpClient.METHOD_POST, engine.lastRequest.method)
        assertEquals(listOf<Pair<Int?, Int>>(HeaderField.RESPONSE_STATUS_OK to 200), listener.sends)
    }

    @Test
    fun aProxiedApnReachesTheCarrierThroughItsProxy() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        transport(engine, apn = proxiedApn).first
            .send(sendReq, Wire.SUBSCRIPTION_ID, RecordingTransportListener())
        assertEquals("proxy.test", engine.lastRequest.proxy?.host)
        assertEquals(8080, engine.lastRequest.proxy?.port)
    }

    @Test
    fun aNullLeaseIsNoNetworkAndIssuesNoRequestAtAll() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        val (subject, factory) = transport(engine, gate = FakeNetworkGate(available = false))
        val listener = RecordingTransportListener()

        assertTrue(subject.send(sendReq, Wire.SUBSCRIPTION_ID, listener))
        assertEquals(MmsResultCode.UNABLE_CONNECT_MMS.code, listener.failures.single().resultCode)
        assertTrue(engine.requests.isEmpty())
        assertEquals(0, factory.created)
    }

    @Test
    fun aLeaseWithNoNetworkIsNoNetworkToo() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        val (subject, factory) = transport(engine, gate = FakeNetworkGate(available = true, handle = null))
        val listener = RecordingTransportListener()

        subject.send(sendReq, Wire.SUBSCRIPTION_ID, listener)
        assertEquals(MmsResultCode.UNABLE_CONNECT_MMS.code, listener.failures.single().resultCode)
        assertTrue(engine.requests.isEmpty())
        assertEquals(0, factory.created)
    }

    @Test
    fun theNetworkLeaseIsReleasedOnceTheExchangeIsDone() {
        val gate = FakeNetworkGate()
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        transport(engine, gate = gate).first.send(sendReq, Wire.SUBSCRIPTION_ID, RecordingTransportListener())
        assertEquals(1, gate.acquisitions)
        assertEquals(1, gate.releases)
    }

    @Test
    fun aCarrierWithNoApnIsNamedRatherThanTurnedIntoAnEmptyUrl() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        val (subject, factory) = transport(engine, apn = null)
        val listener = RecordingTransportListener()

        assertTrue(subject.send(sendReq, Wire.SUBSCRIPTION_ID, listener))
        assertEquals(MmsResultCode.MMS_DISABLED_BY_CARRIER.code, listener.failures.single().resultCode)
        assertTrue(engine.requests.isEmpty())
        assertEquals(0, factory.created)
    }

    @Test
    fun anHttpFailureCarriesItsStatusThrough() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.status(503))
        val listener = RecordingTransportListener()
        transport(engine).first.send(sendReq, Wire.SUBSCRIPTION_ID, listener)

        assertEquals(MmsResultCode.HTTP_FAILURE.code, listener.failures.single().resultCode)
        assertEquals(503, listener.failures.single().httpStatus)
    }

    @Test
    fun aTimeoutIsReportedAsARetryableResultCode() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.timeout())
        val listener = RecordingTransportListener()
        transport(engine).first.send(sendReq, Wire.SUBSCRIPTION_ID, listener)

        assertEquals(MmsResultCode.RETRY.code, listener.failures.single().resultCode)
    }

    @Test
    fun anUnreadableSendConfReportsNoResponseStatusRatherThanGuessing() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(byteArrayOf(0x8C.toByte(), 0x7F)))
        val listener = RecordingTransportListener()
        transport(engine).first.send(sendReq, Wire.SUBSCRIPTION_ID, listener)

        assertEquals(listOf<Pair<Int?, Int>>(null to 200), listener.sends)
    }

    @Test
    fun aRetrieveHandsBackTheParsedConf() {
        val engine = RecordingHttpEngine(
            RecordingHttpEngine.ok(Wire.retrieveConf("text" to "from the carrier")),
        )
        val listener = RecordingTransportListener()
        transport(engine).first.retrieve(Wire.notificationInd(), Wire.SUBSCRIPTION_ID, listener)

        val retrieved = listener.retrieves.single().first
        assertTrue("expected a Retrieve.conf, got $retrieved", retrieved?.isRetrieveConf == true)
        assertEquals("from the carrier", retrieved?.body?.textContent())
        assertEquals(MmscHttpClient.METHOD_GET, engine.lastRequest.method)
        assertEquals("http://mmsc.test/mms/inbox/7", engine.lastRequest.url)
    }

    @Test
    fun theNotificationIsAnsweredWithAnMNotifyRespInd() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        val (subject, _) = transport(engine)

        assertTrue(
            subject.acknowledge(
                Wire.notificationInd("T-ack"),
                HeaderField.STATUS_RETRIEVED,
                Wire.SUBSCRIPTION_ID,
            ),
        )
        val answered = PduParser(engine.lastRequest.body!!).parse()
        assertEquals(MessageType.NOTIFYRESP_IND, answered?.messageType)
        assertEquals(HeaderField.STATUS_RETRIEVED, answered?.status)
        assertEquals("T-ack", answered?.transactionId)
    }

    @Test
    fun aNotificationWithNoTransactionIdCannotBeAnswered() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        val notification = Wire.notificationInd().apply { headers.setText(HeaderField.TRANSACTION_ID, "") }

        assertFalse(transport(engine).first.acknowledge(notification, HeaderField.STATUS_RETRIEVED, Wire.SUBSCRIPTION_ID))
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun theWapProfileMacroIsFilledFromTheSubscriptionsOwnNumber() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        val profiles = profileStoreOf(
            CarrierProfile.KEY_UA_PROFILE_URL to "http://mmsc.test/uaprof.xml?msisdn=##LINE1##",
        )
        transport(engine, profiles = profiles).first.send(sendReq, Wire.SUBSCRIPTION_ID, RecordingTransportListener())

        assertEquals("http://mmsc.test/uaprof.xml?msisdn=$ownNumber", engine.lastRequest.headers["x-wap-profile"])
    }

    @Test
    fun availabilityFollowsWhetherTheApnHasAnMmsc() {
        val engine = RecordingHttpEngine()
        assertTrue(transport(engine).first.isAvailable(Wire.SUBSCRIPTION_ID))
        assertFalse(transport(engine, apn = null).first.isAvailable(Wire.SUBSCRIPTION_ID))
        assertFalse(
            transport(engine, apn = ApnProfile("mms", "", null, null, "mms")).first.isAvailable(Wire.SUBSCRIPTION_ID),
        )
    }

    @Test
    fun theComposedPduIsWhatTheMmscIsSent() {
        val composed = requireNotNull(PduComposer.compose(sendReq))
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        transport(engine).first.send(sendReq, Wire.SUBSCRIPTION_ID, RecordingTransportListener())
        assertTrue(composed.contentEquals(engine.lastRequest.body!!))
    }

    @Test
    fun aSendThatCannotBeComposedNeverReachesTheNetwork() {
        val engine = RecordingHttpEngine(RecordingHttpEngine.ok(Wire.sendConf()))
        val (subject, factory) = transport(engine)
        assertFalse(subject.send(Pdu(MessageType.SEND_CONF), Wire.SUBSCRIPTION_ID, RecordingTransportListener()))
        assertEquals(0, factory.created)
    }
}

/** A [TransportListener] that remembers each of the three outcomes separately. */
class RecordingTransportListener : TransportListener {
    val sends = mutableListOf<Pair<Int?, Int>>()
    val retrieves = mutableListOf<Pair<Pdu?, Int>>()
    val failures = mutableListOf<Failure>()

    data class Failure(val resultCode: Int, val httpStatus: Int)

    override fun onSendCompleted(responseStatus: Int?, httpStatus: Int) {
        sends += responseStatus to httpStatus
    }

    override fun onRetrieveCompleted(retrieveConf: Pdu?, httpStatus: Int) {
        retrieves += retrieveConf to httpStatus
    }

    override fun onFailed(resultCode: Int, httpStatus: Int) {
        failures += Failure(resultCode, httpStatus)
    }
}

/** Counts the engines it builds, so an unissued request is observable. */
class CountingEngineFactory(private val engine: HttpEngine) : HttpEngineFactory {
    var created = 0
        private set

    override fun create(handle: Any?): HttpEngine {
        created++
        return engine
    }
}