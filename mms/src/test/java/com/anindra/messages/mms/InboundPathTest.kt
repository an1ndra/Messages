package com.anindra.messages.mms

import com.anindra.messages.mms.net.CarrierProfile
import com.anindra.messages.mms.net.MmsResultCode
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.spi.AutoDownloadPolicy
import com.anindra.messages.mms.spi.TransportListener
import com.anindra.messages.mms.store.MmsBox
import com.anindra.messages.mms.transport.TransportChoice
import com.anindra.messages.mms.transport.TransportRegistry
import com.anindra.messages.mms.transport.TransportSelection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every terminal branch of the receive path has to answer the carrier.
 *
 * An M-NotifyResp.ind that is never sent is the defect the stack being replaced
 * shipped -- `sendAcknowledgeInd` commented out, and a receiver whose MMSC lookup
 * returned null -- and the symptom is a notification the carrier keeps
 * re-delivering. These go branch by branch and assert the status answered, not
 * only that something was returned.
 */
class InboundPathTest {

    private val now = 1_700_000_000L
    private val notification = Wire.notificationInd(transactionId = "T-notify")

    private fun subject(
        transport: ScriptedTransport,
        store: FakeMmsStore = FakeMmsStore(),
        autoDownload: AutoDownloadPolicy = AutoDownloadPolicy { _, _ -> true },
    ) = InboundFixture(transport, store, autoDownload)

    private class InboundFixture(val transport: ScriptedTransport, val store: FakeMmsStore, val autoDownload: AutoDownloadPolicy) {
        val mms = Mms(
            store = store,
            transports = TransportRegistry(
                listOf(transport),
                TransportSelection(default = requireNotNull(TransportChoice.of(transport.id))),
            ),
            fitter = RecordingFitter.fitting(ByteArray(8)),
            carrierProfiles = profileStoreOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to 300_000),
            autoDownload = autoDownload,
            codec = WspMmsCodec { NOW },
            nowSeconds = { NOW },
        )

        fun receive(pdu: Pdu) = runBlocking { mms.receive(pdu, Wire.SUBSCRIPTION_ID) }
    }

    @Test
    fun aRetrievedAnnouncementIsStoredAndAnswered() {
        val fixture = subject(
            ScriptedTransport(
                onRetrieve = { _, listener ->
                    listener.onRetrieveCompleted(parseOrFail(Wire.retrieveConf("text" to "downloaded")), 200)
                },
            ),
        )
        val outcome = fixture.receive(notification)

        assertEquals(InboundStage.RETRIEVED, outcome.stage)
        assertEquals(HeaderField.STATUS_RETRIEVED, fixture.transport.statusAnsweredFor("T-notify"))
        val stored = fixture.store.persistedIn(MmsBox.INBOX)
        assertNotNull(stored)
        assertEquals(MessageType.RETRIEVE_CONF, stored!!.messageType)
        assertEquals("downloaded", stored.body?.textContent())
    }

    @Test
    fun theAnnouncementRowIsReplacedByTheContentRatherThanKeptAlongside() {
        val fixture = subject(
            ScriptedTransport(
                onRetrieve = { _, listener ->
                    listener.onRetrieveCompleted(parseOrFail(Wire.retrieveConf("text" to "downloaded")), 200)
                },
            ),
        )
        fixture.receive(notification)

        assertEquals(2, fixture.store.persists.size)
        assertEquals(listOf(MessageType.NOTIFICATION_IND, MessageType.RETRIEVE_CONF), fixture.store.persists.map { it.first.messageType })
        assertEquals(1, fixture.store.deletes.size)
    }

    @Test
    fun anExpiredAnnouncementIsAnsweredExpiredAndNotFetched() {
        val transport = ScriptedTransport(
            onRetrieve = { _, _ -> error("an expired announcement must not be fetched") },
        )
        val fixture = subject(transport)
        val outcome = fixture.receive(Wire.notificationInd(expirySeconds = now - 1))

        assertEquals(InboundStage.EXPIRED, outcome.stage)
        assertEquals(HeaderField.STATUS_EXPIRED, transport.statusAnsweredFor("T-notify"))
        assertTrue(transport.retrieves.isEmpty())
        assertEquals(MmsBox.INBOX, fixture.store.persists.single().second)
    }

    @Test
    fun anAnnouncementWithNoExpiryIsNotTreatedAsExpired() {
        val fixture = subject(
            ScriptedTransport(
                onRetrieve = { _, listener ->
                    listener.onRetrieveCompleted(parseOrFail(Wire.retrieveConf("text" to "downloaded")), 200)
                },
            ),
        )
        val undated = Wire.notificationInd().apply {
            headers.longOrNull(HeaderField.EXPIRY)
        }
        val outcome = fixture.receive(undated)
        assertEquals(InboundStage.RETRIEVED, outcome.stage)
    }

    @Test
    fun aDeferredAnnouncementIsAnsweredDeferred() {
        val transport = ScriptedTransport(
            onRetrieve = { _, _ -> error("a deferred announcement is not fetched") },
        )
        val fixture = subject(transport, autoDownload = AutoDownloadPolicy { _, _ -> false })
        val outcome = fixture.receive(notification)

        assertEquals(InboundStage.DEFERRED, outcome.stage)
        assertEquals(HeaderField.STATUS_DEFERRED, transport.statusAnsweredFor("T-notify"))
    }

    @Test
    fun aRetrieveThatCannotEvenStartIsAnsweredDeferred() {
        val transport = ScriptedTransport(starts = false)
        val fixture = subject(transport)
        val outcome = fixture.receive(notification)

        assertEquals(InboundStage.DEFERRED, outcome.stage)
        assertEquals(HeaderField.STATUS_DEFERRED, transport.statusAnsweredFor("T-notify"))
    }

    @Test
    fun aFailedRetrieveIsAnsweredRejected() {
        val fixture = subject(
            ScriptedTransport(onRetrieve = { _, listener ->
                listener.onFailed(MmsResultCode.UNABLE_CONNECT_MMS.code, 0)
            }),
        )
        val outcome = fixture.receive(notification)

        assertEquals(InboundStage.FAILED, outcome.stage)
        assertEquals(HeaderField.STATUS_REJECTED, fixture.transport.statusAnsweredFor("T-notify"))
    }

    @Test
    fun anUnusableBodyIsAnsweredRejectedAndTheAnnouncementIsKept() {
        val fixture = subject(
            ScriptedTransport(onRetrieve = { _, listener -> listener.onRetrieveCompleted(null, 200) }),
        )
        val outcome = fixture.receive(notification)

        assertEquals(InboundStage.FAILED, outcome.stage)
        assertEquals(HeaderField.STATUS_REJECTED, fixture.transport.statusAnsweredFor("T-notify"))
        assertEquals(1, fixture.store.persists.size)
        assertTrue(fixture.store.deletes.isEmpty())
    }

    @Test
    fun aRetrieveConfWithNoContentIsNotStoredAsAMessage() {
        val headerOnly = Pdu(MessageType.RETRIEVE_CONF).apply {
            headers.setContentType("application/vnd.wap.multipart.related")
            headers.setLong(HeaderField.DATE, now)
            headers.setOctet(
                HeaderField.RETRIEVE_STATUS,
                HeaderField.RETRIEVE_STATUS_ERROR_PERMANENT_MESSAGE_NOT_FOUND,
            )
        }
        val fixture = subject(
            ScriptedTransport(onRetrieve = { _, listener -> listener.onRetrieveCompleted(headerOnly, 200) }),
        )
        val outcome = fixture.receive(notification)

        assertEquals(InboundStage.FAILED, outcome.stage)
        assertEquals(1, fixture.store.persists.size)
        assertEquals(HeaderField.STATUS_REJECTED, fixture.transport.statusAnsweredFor("T-notify"))
    }

    @Test
    fun anAnnouncementThatCannotBeStoredIsStillAnswered() {
        val fixture = subject(
            ScriptedTransport(onRetrieve = { _, _ -> error("nothing to retrieve") }),
            store = FakeMmsStore(acceptsPersist = false),
        )
        val outcome = fixture.receive(notification)

        assertEquals(InboundStage.NOT_STORED, outcome.stage)
        assertEquals(HeaderField.STATUS_REJECTED, fixture.transport.statusAnsweredFor("T-notify"))
    }

    @Test
    fun somethingThatIsNotAnAnnouncementIsNotAnswered() {
        val fixture = subject(ScriptedTransport())
        val outcome = fixture.receive(Wire.notificationInd().apply { messageType = MessageType.READ_REC_IND })

        assertEquals(InboundStage.NOT_A_NOTIFICATION, outcome.stage)
        assertTrue(fixture.transport.acknowledgements.isEmpty())
        assertTrue(fixture.store.persists.isEmpty())
    }

    @Test
    fun anUnavailableTransportIsAnsweredDeferredWithoutStoring() {
        val transport = ScriptedTransport(available = false)
        val fixture = subject(transport)
        val outcome = fixture.receive(notification)

        assertEquals(InboundStage.DEFERRED, outcome.stage)
        assertEquals(HeaderField.STATUS_DEFERRED, transport.statusAnsweredFor("T-notify"))
        assertTrue(fixture.store.persists.isEmpty())
        assertTrue(transport.retrieves.isEmpty())
    }

    @Test
    fun thePlatformPathLeavesTheRowAndTheAnswerToThePlatform() {
        val transport = ScriptedTransport(
            id = TransportChoice.SYSTEM.id,
            reportsThroughPendingIntent = true,
            ownsNotificationRow = true,
        )
        val fixture = subject(transport)
        val outcome = fixture.receive(notification)

        assertEquals(InboundStage.AWAITING_PLATFORM, outcome.stage)
        assertEquals(1, fixture.transport.retrieves.size)
        assertTrue("the platform already wrote this row", fixture.store.persists.isEmpty())
        assertTrue(fixture.transport.acknowledgements.isEmpty())
    }

    @Test
    fun autoDownloadFollowsTheUserSettingAndRoaming() {
        val policy = RoamingAwareAutoDownload(
            enabled = { true },
            roaming = { it == 2 },
        )
        assertTrue(policy.autoDownload(notification, 1))
        assertTrue(!policy.autoDownload(notification, 2))
        assertTrue(!RoamingAwareAutoDownload(enabled = { false }).autoDownload(notification, 1))
    }

    @Test
    fun aRetrieveThatReportsTwiceStillSettlesTheAnnouncement() {
        val transport = ScriptedTransport(
            onRetrieve = { _, listener ->
                listener.onFailed(MmsResultCode.RETRY.code, 0)
                listener.onRetrieveCompleted(parseOrFail(Wire.retrieveConf("text" to "downloaded")), 200)
            },
        )
        val fixture = subject(transport)
        val outcome = fixture.receive(notification)

        assertEquals(InboundStage.FAILED, outcome.stage)
        assertEquals(HeaderField.STATUS_REJECTED, transport.statusAnsweredFor("T-notify"))
    }

    private companion object {
        const val NOW = 1_700_000_000L
    }
}