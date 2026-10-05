package com.anindra.messages.mms

import com.anindra.messages.mms.net.CarrierProfile
import com.anindra.messages.mms.net.MmsResultCode
import com.anindra.messages.mms.net.MapCarrierValues
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduComposer
import com.anindra.messages.mms.pdu.PduParser
import com.anindra.messages.mms.debug.MmsDebugRecorder
import com.anindra.messages.mms.spi.AutoDownloadPolicy
import com.anindra.messages.mms.spi.BudgetPolicy
import com.anindra.messages.mms.spi.FitOutcome
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
 * The orchestration is where a send becomes a row and a notification becomes a
 * message. Two things are worth pinning harder than the rest: that the attachment
 * budget has exactly one owner, and that every terminal branch of the receive
 * path answers the carrier -- an unanswered notification is re-delivered.
 */
class MmsFacadeTest {

    private val store = FakeMmsStore()
    private val caption = "a caption"
    private val now = 1_700_000_000L
    private val profiles = profileStoreOf(
        CarrierProfile.KEY_MAX_MESSAGE_SIZE to 40_000,
        CarrierProfile.KEY_MAX_IMAGE_WIDTH to 1_024,
        CarrierProfile.KEY_MAX_IMAGE_HEIGHT to 768,
    )

    private fun message(
        caption: String = this.caption,
        subscriptionId: Int = Wire.SUBSCRIPTION_ID,
    ) = OutgoingMessage(
        addresses = listOf("+15551230000"),
        caption = caption,
        attachmentMimeType = "image/jpeg",
        attachmentBytes = ByteArray(2_048),
        sourceWidth = 4_000,
        sourceHeight = 3_000,
        subscriptionId = subscriptionId,
    )

    private fun mms(
        transport: ScriptedTransport = acceptedTransport(),
        fitter: RecordingFitter = RecordingFitter.fitting(ByteArray(2_048)),
        autoDownload: AutoDownloadPolicy = AutoDownloadPolicy { _, _ -> true },
        store: FakeMmsStore = this.store,
    ) = Mms(
        store = store,
        transports = TransportRegistry(listOf(transport), TransportSelection(default = choiceOf(transport))),
        fitter = fitter,
        carrierProfiles = profiles,
        autoDownload = autoDownload,
        codec = WspMmsCodec { now },
        nowSeconds = { now },
    )

    private fun choiceOf(transport: ScriptedTransport) = requireNotNull(TransportChoice.of(transport.id))

    private fun acceptedTransport(
        responseStatus: Int = HeaderField.RESPONSE_STATUS_OK,
    ) = ScriptedTransport(
        onSend = { _, listener -> listener.onSendCompleted(responseStatus, 200) },
        onRetrieve = { _, listener ->
            listener.onRetrieveCompleted(parseOrFail(Wire.retrieveConf("text" to "downloaded")), 200)
        },
    )

    @Test
    fun theAttachmentBudgetHasOneOwner() {
        val fitter = RecordingFitter.fitting(ByteArray(2_048))
        runBlocking { mms(fitter = fitter).send(message()) }

        val fit = fitter.requests.single()
        assertEquals(
            BudgetPolicy.attachmentBudget(
                carrierMaxMessageSize = 40_000,
                captionBytes = caption.toByteArray(Charsets.UTF_8).size,
                partCount = SendReqBuilder.partCount(caption),
            ),
            fit.budgetBytes,
        )
        assertEquals(1_024, fit.maxImageWidth)
        assertEquals(768, fit.maxImageHeight)
        assertEquals(4_000, fit.sourceWidth)
        assertEquals(3_000, fit.sourceHeight)
    }

    @Test
    fun anAttachmentThatCannotBeReadAndOneThatIsTooLargeAreDifferentOutcomes() {
        val unreadable = runBlocking {
            mms(fitter = RecordingFitter { FitOutcome.Unreadable }).send(message())
        }
        val tooLarge = runBlocking {
            mms(fitter = RecordingFitter { FitOutcome.TooLarge }).send(message())
        }

        assertEquals(SendOutcome.Rejected(SendRejection.ATTACHMENT_UNREADABLE), unreadable)
        assertEquals(SendOutcome.Rejected(SendRejection.ATTACHMENT_TOO_LARGE), tooLarge)
    }

    @Test
    fun anAttachmentThatCannotBeReadNeverReachesTheTransport() {
        val transport = acceptedTransport()
        runBlocking { mms(transport = transport, fitter = RecordingFitter { FitOutcome.Unreadable }).send(message()) }
        assertTrue(transport.sends.isEmpty())
        assertTrue(store.persists.isEmpty())
    }

    @Test
    fun aSendIsPersistedToTheOutboxBeforeItIsHandedOver() {
        runBlocking { mms().send(message()) }
        val (pdu, box, subscriptionId) = store.persists.single()
        assertEquals(MmsBox.OUTBOX, box)
        assertEquals(Wire.SUBSCRIPTION_ID, subscriptionId)
        assertEquals(MessageType.SEND_REQ, pdu.messageType)
    }

    @Test
    fun anAcceptedSendMovesTheRowToSent() {
        val outcome = runBlocking { mms().send(message()) }
        val sent = outcome as SendOutcome.Sent
        assertEquals(HeaderField.RESPONSE_STATUS_OK, sent.responseStatus)
        assertEquals(MmsBox.SENT, store.moves.single().second)
    }

    @Test
    fun aRefusedSendMovesTheRowToFailedAndRecordsWhy() {
        val outcome = runBlocking {
            mms(transport = ScriptedTransport(onSend = { _, listener ->
                listener.onFailed(MmsResultCode.HTTP_FAILURE.code, 503)
            })).send(message())
        }
        val failed = outcome as SendOutcome.Failed
        assertEquals(MmsResultCode.HTTP_FAILURE.code, failed.resultCode)
        assertEquals(503, failed.httpStatus)
        assertEquals(listOf(MmsResultCode.HTTP_FAILURE.code), store.pendingErrors.map { it.second })
        assertTrue(store.moves.isEmpty())
    }

    @Test
    fun aTransientResponseStatusIsRecordedAsRetryable() {
        val outcome = runBlocking {
            mms(transport = acceptedTransport(HeaderField.RESPONSE_STATUS_ERROR_TRANSIENT_NETWORK_PROBLEM))
                .send(message())
        }
        assertEquals(MmsResultCode.RETRY.code, (outcome as SendOutcome.Failed).resultCode)
    }

    @Test
    fun aPermanentResponseStatusIsNotRetryable() {
        val outcome = runBlocking {
            mms(transport = acceptedTransport(HeaderField.RESPONSE_STATUS_ERROR_PERMANENT_FAILURE))
                .send(message())
        }
        assertEquals(MmsResultCode.UNSPECIFIED.code, (outcome as SendOutcome.Failed).resultCode)
    }

    @Test
    fun anUnreadableResponseStatusIsNotTreatedAsDelivered() {
        val outcome = runBlocking {
            mms(transport = ScriptedTransport(onSend = { _, listener -> listener.onSendCompleted(null, 200) })).send(message())
        }
        assertEquals(MmsResultCode.UNSPECIFIED.code, (outcome as SendOutcome.Failed).resultCode)
        assertTrue(store.moves.isEmpty())
    }

    @Test
    fun anUnrenderableAttachmentIsRefusedBeforeAnythingIsPersisted() {
        val fitter = RecordingFitter { FitOutcome.Fitted(ByteArray(8), "application/octet-stream", 0, 0) }
        val outcome = runBlocking { mms(fitter = fitter).send(message(caption = "")) }

        assertEquals(SendOutcome.Rejected(SendRejection.NO_PRESENTATION), outcome)
        assertTrue(store.persists.isEmpty())
    }

@Test
    fun aMessageTheCarrierWillNotTakeLeavesNoRowBehind() {
        val fitter = RecordingFitter.fitting(ByteArray(40_000))
        val outcome = runBlocking { mms(fitter = fitter).send(message()) }

        assertEquals(SendOutcome.Rejected(SendRejection.TOO_LARGE), outcome)
        assertEquals(1, store.deletes.size)
        assertTrue(store.moves.isEmpty())
    }

    @Test
    fun aMessageWithNoRecipientIsNeverPersisted() {
        val outcome = runBlocking { mms().send(message().copy(addresses = emptyList())) }
        assertEquals(SendOutcome.Rejected(SendRejection.NO_RECIPIENTS), outcome)
        assertTrue(store.persists.isEmpty())
    }

    @Test
    fun noTransportMeansNoRow() {
        val outcome = runBlocking {
            Mms(
                store = store,
                transports = TransportRegistry(emptyList()),
                fitter = RecordingFitter.fitting(ByteArray(2_048)),
                carrierProfiles = profiles,
                autoDownload = AutoDownloadPolicy { _, _ -> true },
                nowSeconds = { now },
            ).send(message())
        }
        assertEquals(SendOutcome.Rejected(SendRejection.NO_TRANSPORT), outcome)
        assertTrue(store.deletes.size == 1)
    }

    @Test
    fun thePlatformPathIsQueuedRatherThanWaitedOn() {
        val platform = ScriptedTransport(
            id = TransportChoice.SYSTEM.id,
            reportsThroughPendingIntent = true,
            onSend = { _, _ -> error("the platform path reports through a PendingIntent") },
        )
        val outcome = runBlocking { mms(transport = platform).send(message()) }

        assertTrue(outcome is SendOutcome.Queued)
        assertTrue(store.moves.isEmpty())
    }

    @Test
    fun theCarrierReportHeadersComeFromItsConfig() {
        val carrierValues = MapCarrierValues(
            mapOf(
                SendHeaderPolicy.KEY_DELIVERY_REPORT to true,
                SendHeaderPolicy.KEY_READ_REPORT to false,
            ),
        )
        val subject = Mms(
            store = store,
            transports = TransportRegistry(
                listOf(acceptedTransport()),
                TransportSelection(default = TransportChoice.DIRECT),
            ),
            fitter = RecordingFitter.fitting(ByteArray(64)),
            carrierProfiles = profileStoreOf(CarrierProfile.KEY_MAX_MESSAGE_SIZE to 300_000),
            autoDownload = AutoDownloadPolicy { _, _ -> true },
            carrierConfig = { carrierValues },
            nowSeconds = { now },
        )
        runBlocking { subject.send(message()) }

        val persisted = store.persistedIn(MmsBox.OUTBOX)!!
        assertEquals(HeaderField.VALUE_YES, persisted.headers.octetOrNull(HeaderField.DELIVERY_REPORT))
        assertEquals(HeaderField.VALUE_NO, persisted.headers.octetOrNull(HeaderField.READ_REPORT))
    }

    @Test
    fun diagnosticsRecordsTheSendSequence() {
        val recorder = MmsDebugRecorder()
        val transport = acceptedTransport()
        val subject = Mms(
            store = store,
            transports = TransportRegistry(
                listOf(transport),
                TransportSelection(default = requireNotNull(TransportChoice.of(transport.id))),
            ),
            fitter = RecordingFitter.fitting(ByteArray(2_048)),
            carrierProfiles = profiles,
            autoDownload = AutoDownloadPolicy { _, _ -> true },
            nowSeconds = { now },
            diagnostics = recorder,
        )
        runBlocking { subject.send(message()) }

        val categories = recorder.snapshot().map { it.category }
        assertTrue(categories.contains("send"))
        assertTrue(categories.contains("transport"))
    }

    @Test
    fun anAnnouncementIsStoredSoItIsNotLostWhenItIsNotFetched() {
        val transport = ScriptedTransport(
            onRetrieve = { _, _ -> error("a deferred announcement is not fetched") },
        )
        val subject = Mms(
            store = store,
            transports = TransportRegistry(
                listOf(transport),
                TransportSelection(default = requireNotNull(TransportChoice.of(transport.id))),
            ),
            fitter = RecordingFitter.fitting(ByteArray(64)),
            carrierProfiles = profiles,
            autoDownload = AutoDownloadPolicy { _, _ -> false },
            nowSeconds = { now },
        )
        val outcome = runBlocking { subject.receive(Wire.notificationInd(), Wire.SUBSCRIPTION_ID) }

        assertEquals(InboundStage.DEFERRED, outcome.stage)
        assertEquals(MmsBox.INBOX, store.persists.single().second)
        assertTrue(transport.retrieves.isEmpty())
    }
}