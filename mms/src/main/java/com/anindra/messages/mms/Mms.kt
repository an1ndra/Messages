package com.anindra.messages.mms

import android.net.Uri
import com.anindra.messages.mms.net.CarrierConfigSource
import com.anindra.messages.mms.net.CarrierConfigValues
import com.anindra.messages.mms.net.CarrierProfile
import com.anindra.messages.mms.net.CarrierProfileStore
import com.anindra.messages.mms.net.MapCarrierValues
import com.anindra.messages.mms.net.MmsResultCode
import com.anindra.messages.mms.pdu.CharacterSets
import com.anindra.messages.mms.pdu.ContentTypes
import com.anindra.messages.mms.pdu.EncodedStringValue
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.pdu.PduBody
import com.anindra.messages.mms.pdu.PduComposer
import com.anindra.messages.mms.pdu.PduParser
import com.anindra.messages.mms.pdu.PduPart
import com.anindra.messages.mms.smil.SmilBuilder
import com.anindra.messages.mms.smil.SmilSerializer
import com.anindra.messages.mms.spi.AttachmentFitter
import com.anindra.messages.mms.spi.AutoDownloadPolicy
import com.anindra.messages.mms.spi.BudgetPolicy
import com.anindra.messages.mms.spi.FitOutcome
import com.anindra.messages.mms.spi.FitRequest
import com.anindra.messages.mms.spi.MmsDiagnostics
import com.anindra.messages.mms.spi.MmsPduCodec
import com.anindra.messages.mms.spi.TransportListener
import com.anindra.messages.mms.store.MmsBox
import com.anindra.messages.mms.store.MmsStore
import com.anindra.messages.mms.transport.TransportBinding
import com.anindra.messages.mms.transport.TransportRegistry
import kotlinx.coroutines.CompletableDeferred

/**
 * [MmsPduCodec] over this package's own [PduComposer] and [PduParser].
 *
 * The clock is injected because a relative `Expiry` is resolved against it on
 * decode, and a test that cannot move the clock cannot tell an expiry of
 * "60 seconds from now" from one that landed in 1970.
 */
class WspMmsCodec(
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : MmsPduCodec {
    override fun compose(pdu: Pdu): ByteArray? = PduComposer.compose(pdu)

    override fun parse(bytes: ByteArray): Pdu? = PduParser(bytes, nowSeconds = nowSeconds).parse()
}

/**
 * The `From` address for a subscription.
 *
 * Most carriers fill the sender in from the SIM, in which case the PDU carries
 * the insert-address token and this returns null. Some want the MSISDN written
 * out, and a subscription whose number cannot be read falls back to the token
 * rather than sending an empty address.
 */
fun interface SendAddressSource {
    fun addressOf(subscriptionId: Int): String?
}

/**
 * The outbound headers that describe the message rather than the transaction.
 *
 * The delivery and read report headers are the carrier's to decide and come from
 * its config, which is why [withCarrierConfig] is the only place that reads
 * them. Message class, expiry and priority describe what is being sent rather
 * than what the carrier will accept, so they are the caller's to choose and are
 * carried here rather than as constants in the builder.
 */
data class SendHeaderPolicy(
    val messageClass: Int,
    val expirySeconds: Long,
    val priority: Int,
    val deliveryReport: Int,
    val readReport: Int,
) {
    companion object {
        const val KEY_DELIVERY_REPORT = "enableMMSDeliveryReports"
        const val KEY_READ_REPORT = "enableMMSReadReports"

        /** A week: the default every carrier config documents, and the AOSP baseline. */
        const val DEFAULT_EXPIRY_SECONDS = 7L * 24 * 60 * 60

        /**
         * Personal, normal priority, no reports asked for.
         *
         * Reports are off by default because requesting one obliges the sending
         * handset to answer the carrier's `M-Delivery.ind`, which is a second
         * transaction this module does not run.
         */
        val DEFAULT = SendHeaderPolicy(
            messageClass = HeaderField.MESSAGE_CLASS_PERSONAL,
            expirySeconds = DEFAULT_EXPIRY_SECONDS,
            priority = HeaderField.PRIORITY_NORMAL,
            deliveryReport = HeaderField.VALUE_NO,
            readReport = HeaderField.VALUE_NO,
        )

        /** [base] with the two report headers the carrier asked for. */
        fun withCarrierConfig(base: SendHeaderPolicy, values: CarrierConfigValues): SendHeaderPolicy =
            base.copy(
                deliveryReport = if (values.boolean(KEY_DELIVERY_REPORT, false)) {
                    HeaderField.VALUE_YES
                } else {
                    HeaderField.VALUE_NO
                },
                readReport = if (values.boolean(KEY_READ_REPORT, false)) {
                    HeaderField.VALUE_YES
                } else {
                    HeaderField.VALUE_NO
                },
            )
    }
}

/** An attachment as it will go on the wire, after fitting. */
class FittedAttachment(val mimeType: String, val bytes: ByteArray)

/** Everything a SendReq is assembled from. */
data class SendReqRequest(
    val addresses: List<String>,
    val caption: String,
    val attachment: FittedAttachment,
    val profile: CarrierProfile,
    val subscriptionId: Int,
    val headers: SendHeaderPolicy,
)

/** Why a SendReq could not be built, which is not the same as a failed send. */
sealed interface SendReqOutcome {
    data class Built(val pdu: Pdu) : SendReqOutcome
    data object NoRecipients : SendReqOutcome
    data object GroupMmsUnsupported : SendReqOutcome

    /**
     * Nothing in the body can be laid out.
     *
     * SMIL has one element per renderable media type, so an attachment that is
     * none of them -- a vCard, an application/octet-stream -- has no item to
     * reference. Emitting a slideshow with nothing in it renders as an empty
     * message on the receiving handset, which is worse than saying so here.
     */
    data object NoPresentation : SendReqOutcome
}

/**
 * Assembles the `M-Send.req` for an outgoing message.
 *
 * Every recipient gets its own `To` header, because that is how a group message
 * is addressed -- one header holding a packed list is a different field, and
 * collapsing them loses the group. The SMIL part leads the body, the caption
 * becomes a `text/plain` part beside the attachment, and the message size,
 * message class, expiry, priority and report headers all come from the request,
 * so nothing here is a constant that a carrier could contradict.
 *
 * Free of `android.*` types: every input is either a value or a [CarrierProfile],
 * which is itself android-free.
 */
object SendReqBuilder {

    /** The content type the whole message is declared as. */
    const val MULTIPART_CONTENT_TYPE = ContentTypes.MULTIPART_RELATED

    /** Body index of the SMIL part, which is expected to lead. */
    const val SMIL_PART_INDEX = 0

    const val TEXT_PART_NAME = "text"

    /**
     * Parts a body for [caption] holds: the attachment, the SMIL, and the
     * caption when there is one. The budget is derived from this, so it lives
     * here rather than being counted a second time by the caller.
     */
    fun partCount(caption: String): Int = MEDIA_PART_COUNT + SMIL_PART_COUNT + if (caption.isNotBlank()) TEXT_PART_COUNT else 0

    fun build(request: SendReqRequest, sender: SendAddressSource, nowSeconds: Long): SendReqOutcome {
        val recipients = request.addresses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (recipients.isEmpty()) return SendReqOutcome.NoRecipients
        if (recipients.size > 1 && !request.profile.groupMmsEnabled()) {
            return SendReqOutcome.GroupMmsUnsupported
        }

        val body = PduBody()
        body.add(mediaPart(request.attachment))
        if (request.caption.isNotBlank()) body.add(textPart(request.caption))
        val smil = SmilBuilder.build(body) ?: return SendReqOutcome.NoPresentation
        body.addAt(SMIL_PART_INDEX, smilPart(SmilSerializer.serialize(smil)))

        val pdu = Pdu(MessageType.SEND_REQ)
        pdu.headers.setContentType(MULTIPART_CONTENT_TYPE)
        pdu.from = sender.addressOf(request.subscriptionId)
            ?.takeIf { it.isNotBlank() }
            ?.let { EncodedStringValue(CharacterSets.UTF_8, it) }
            ?: EncodedStringValue.insertAddressToken()
        recipients.forEach { pdu.headers.addEncoded(HeaderField.TO, EncodedStringValue(CharacterSets.UTF_8, it)) }
        pdu.headers.setLong(HeaderField.DATE, nowSeconds)
        pdu.headers.setLong(HeaderField.MESSAGE_SIZE, body.totalDataBytes())
        pdu.headers.setMessageClassOctet(request.headers.messageClass)
        pdu.headers.setLong(HeaderField.EXPIRY, request.headers.expirySeconds)
        pdu.headers.setOctet(HeaderField.PRIORITY, request.headers.priority)
        pdu.headers.setOctet(HeaderField.DELIVERY_REPORT, request.headers.deliveryReport)
        pdu.headers.setOctet(HeaderField.READ_REPORT, request.headers.readReport)
        pdu.headers.setText(HeaderField.TRANSACTION_ID, transactionIdFor(request, nowSeconds))
        pdu.body = body
        return SendReqOutcome.Built(pdu)
    }

    /**
     * A transaction id the receiving MMSC has not seen before.
     *
     * The transaction id is the MMSC's only deduplication key: an outgoing
     * M-Send.conf is matched to this request by it, and a carrier that has
     * already answered the same id will not answer again.
     */
    private fun transactionIdFor(request: SendReqRequest, nowSeconds: Long): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val material = buildString {
            append(nowSeconds).append('@')
            request.addresses.forEach { append(it.trim()).append(',') }
            append('#').append(request.headers.messageClass)
            // Two sends in the same second to the same recipient are still two
            // transactions, and the id is the MMSC's only deduplication key.
            append('+').append(nextNonce())
        }
        return digest.digest(material.toByteArray(Charsets.UTF_8))
            .take(TRANSACTION_ID_BYTES)
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Distinguishes two otherwise identical sends. Seeded from the clock so a
     * restarted process does not repeat the previous run's ids.
     */
    private val nonce = java.util.concurrent.atomic.AtomicLong(System.nanoTime())

    private fun nextNonce(): Long = nonce.incrementAndGet()

    private fun mediaPart(attachment: FittedAttachment) = PduPart().apply {
        contentType = ContentTypes.normalize(attachment.mimeType)
        name = mediaPartName(contentType!!)
        contentId = "<$name>"
        data = attachment.bytes
    }

    private fun textPart(caption: String) = PduPart().apply {
        contentType = TEXT_CONTENT_TYPE
        name = TEXT_PART_NAME
        contentId = "<$name>"
        charset = CharacterSets.UTF_8
        data = caption.toByteArray(Charsets.UTF_8)
    }

    private fun smilPart(data: ByteArray) = PduPart().apply {
        contentType = PduPart.APP_SMIL
        name = PduPart.DEFAULT_SMIL_NAME
        contentId = "<${PduPart.DEFAULT_SMIL_NAME}>"
        this.data = data
    }

    /**
     * The name the attachment part is written and referred to under.
     *
     * A part needs one, and the name is what a SMIL `src` resolves against, so
     * it has to be stable and it has to say what the media is rather than be a
     * generic placeholder.
     */
    private fun mediaPartName(contentType: String): String = when {
        ContentTypes.isImage(contentType) -> "image"
        ContentTypes.isVideo(contentType) -> "video"
        ContentTypes.isAudio(contentType) -> "audio"
        else -> "attachment"
    }

    const val TEXT_CONTENT_TYPE = "text/plain"

    const val SMIL_PART_COUNT = 1
    const val MEDIA_PART_COUNT = 1
    const val TEXT_PART_COUNT = 1
    const val TRANSACTION_ID_BYTES = 16
}

/** What an outgoing send ended as. */
sealed interface SendOutcome {
    /** The MMSC accepted the message and the outbox row has moved to sent. */
    data class Sent(val messageUri: Uri, val responseStatus: Int) : SendOutcome

    /** The row is in the failed box and carries [resultCode] for the retry policy. */
    data class Failed(val messageUri: Uri, val resultCode: Int, val httpStatus: Int) : SendOutcome

    /**
     * The row is still in the outbox and the result will arrive as a broadcast.
     *
     * Only the platform path ends here: it is handed the completion
     * PendingIntent and fills in the result itself, so there is no callback to
     * wait on and no answer to give yet.
     */
    data class Queued(val messageUri: Uri) : SendOutcome

    /** Nothing was attempted, so there is no row and nothing to retry. */
    data class Rejected(val reason: SendRejection) : SendOutcome
}

enum class SendRejection {
    ATTACHMENT_UNREADABLE,
    ATTACHMENT_TOO_LARGE,
    NO_RECIPIENTS,
    GROUP_MMS_UNSUPPORTED,
    NO_PRESENTATION,
    NOT_STOREABLE,
    NOT_COMPOSABLE,
    TOO_LARGE,
    NO_TRANSPORT,
    NOT_STARTED,
}

/** Where an inbound transaction ended. */
enum class InboundStage {
    /** Carried no M-Notification.ind, so there was nothing to answer. */
    NOT_A_NOTIFICATION,

    /** No transport is configured for the subscription, so nothing can be said to the carrier. */
    NO_TRANSPORT,

    /** The announcement could not be stored, and neither could its content later. */
    NOT_STORED,

    /** The carrier's own expiry had already passed when the notification arrived. */
    EXPIRED,

    /** Left for the user or for a scheduled retry to fetch. */
    DEFERRED,

    /** Handed to the platform, which reports through the app's PendingIntent. */
    AWAITING_PLATFORM,

    /** Content fetched and stored. */
    RETRIEVED,

    /** The retrieve was attempted and did not produce usable content. */
    FAILED,
}

/** The outcome of receiving an M-Notification.ind. */
sealed interface InboundOutcome {
    val stage: InboundStage

    data class Resolved(override val stage: InboundStage, val messageUri: Uri?) : InboundOutcome
}

/**
 * Decides whether an announced message is fetched now.
 *
 * Roaming is the one condition that has to be asked about rather than derived,
 * because it is the only one whose answer is not already on the notification.
 */
class RoamingAwareAutoDownload(
    private val enabled: () -> Boolean = { true },
    private val roaming: (Int) -> Boolean = { false },
) : AutoDownloadPolicy {
    override fun autoDownload(notification: Pdu, subscriptionId: Int): Boolean =
        enabled() && !roaming(subscriptionId)
}

/** The result of one transport call, after however many attempts it took. */
private sealed interface TransferResult {
    /** The MMSC answered; [responseStatus] is null when the answer was unreadable. */
    data class Answered(val responseStatus: Int?, val httpStatus: Int) : TransferResult

    data class Failed(val resultCode: Int, val httpStatus: Int) : TransferResult

    /** The send was attempted and the MMSC answered with content, or with an unusable body. */
    data class Retrieved(val pdu: Pdu?, val httpStatus: Int) : TransferResult
}

/** What the app asked to send, before any carrier limit has been applied to it. */
data class OutgoingMessage(
    val addresses: List<String>,
    val caption: String,
    val attachmentMimeType: String,
    val attachmentBytes: ByteArray,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val subscriptionId: Int,
)

/**
 * The one entry point for MMS: fit, build, persist, hand to a transport, and act
 * on the result.
 *
 * Everything above this class is transport-agnostic. The transport is chosen per
 * subscription at call time from a [TransportRegistry], so a carrier behaving
 * differently on one path is a settings change rather than a code change, and
 * nothing here branches on which transport it got beyond asking the binding what
 * it owns.
 */
class Mms(
    private val store: MmsStore,
    private val transports: TransportRegistry,
    private val fitter: AttachmentFitter,
    private val carrierProfiles: CarrierProfileStore,
    private val autoDownload: AutoDownloadPolicy,
    private val codec: MmsPduCodec = WspMmsCodec(),
    private val sendAddress: SendAddressSource = SendAddressSource { null },
    private val carrierConfig: CarrierConfigSource = CarrierConfigSource { null },
    private val diagnostics: MmsDiagnostics = object : MmsDiagnostics {},
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    /**
     * Fits the attachment to the carrier, assembles the PDU, and hands it over.
     *
     * The order matters: the size has to be settled before the row is written,
     * because a message the carrier will not accept must not leave a row behind
     * for the user to retry forever.
     */
    suspend fun send(message: OutgoingMessage): SendOutcome {
        val profile = carrierProfiles.of(message.subscriptionId)
        val fitted = when (val outcome = fit(message, profile)) {
            is FitOutcome.Fitted -> outcome
            FitOutcome.Unreadable -> return SendOutcome.Rejected(SendRejection.ATTACHMENT_UNREADABLE)
            FitOutcome.TooLarge -> return SendOutcome.Rejected(SendRejection.ATTACHMENT_TOO_LARGE)
        }
        val built = SendReqBuilder.build(
            SendReqRequest(
                addresses = message.addresses,
                caption = message.caption,
                attachment = FittedAttachment(fitted.mimeType, fitted.bytes),
                profile = profile,
                subscriptionId = message.subscriptionId,
                headers = sendHeadersFor(message.subscriptionId),
            ),
            sendAddress,
            nowSeconds(),
        )
        val pdu = when (built) {
            is SendReqOutcome.Built -> built.pdu
            SendReqOutcome.NoRecipients -> return SendOutcome.Rejected(SendRejection.NO_RECIPIENTS)
            SendReqOutcome.GroupMmsUnsupported -> return SendOutcome.Rejected(SendRejection.GROUP_MMS_UNSUPPORTED)
            SendReqOutcome.NoPresentation -> return SendOutcome.Rejected(SendRejection.NO_PRESENTATION)
        }

        val outbox = store.persist(pdu, MmsBox.OUTBOX, message.subscriptionId)
            ?: return SendOutcome.Rejected(SendRejection.NOT_STOREABLE)

        val bytes = codec.compose(pdu)
        if (bytes == null) {
            store.delete(outbox)
            return SendOutcome.Rejected(SendRejection.NOT_COMPOSABLE)
        }
        diagnostics.sendBuilt(bytes.size, pdu.to.size)
        if (bytes.size.toLong() > profile.maxMessageSize().toLong()) {
            store.delete(outbox)
            return SendOutcome.Rejected(SendRejection.TOO_LARGE)
        }

        val binding = transports.assign(message.subscriptionId)
        if (binding == null) {
            store.delete(outbox)
            return SendOutcome.Rejected(SendRejection.NO_TRANSPORT)
        }
        val available = binding.transport.isAvailable(message.subscriptionId)
        diagnostics.transportSelected(binding.choice.id, message.subscriptionId, available)
        if (!available) {
            store.delete(outbox)
            return SendOutcome.Rejected(SendRejection.NO_TRANSPORT)
        }
        diagnostics.sendStarted(binding.choice.id, message.subscriptionId, outbox.toString())

        val listener = TransferListener()
        if (!binding.transport.send(pdu, message.subscriptionId, listener)) {
            diagnostics.sendCompleted(binding.choice.id, "not_started", null, 0)
            store.delete(outbox)
            return SendOutcome.Rejected(SendRejection.NOT_STARTED)
        }
        // The platform path reports through the PendingIntent it was handed, so
        // suspending on the listener would wait for a callback nobody makes.
        if (binding.reportsThroughPendingIntent) {
            diagnostics.sendCompleted(binding.choice.id, "queued", null, 0)
            return SendOutcome.Queued(outbox)
        }
        val outcome = awaitSend(outbox, listener)
        when (outcome) {
            is SendOutcome.Sent -> diagnostics.sendCompleted(
                binding.choice.id, "sent", outcome.responseStatus, 0,
            )
            is SendOutcome.Failed -> diagnostics.sendCompleted(
                binding.choice.id, "failed", null, outcome.httpStatus,
            )
            else -> diagnostics.sendCompleted(binding.choice.id, outcome.javaClass.simpleName, null, 0)
        }
        return outcome
    }

    /**
     * Takes an M-Notification.ind, stores it, and answers it on every branch.
     *
     * The answer is the part that must not be missed: a carrier that gets no
     * M-NotifyResp.ind re-delivers the notification, so a branch that returned
     * quietly would loop. Retrieved, expired, deferred, unstorable and failed
     * each say so explicitly.
     */
    suspend fun receive(notification: Pdu, subscriptionId: Int): InboundOutcome {
        if (!notification.isNotificationInd) return InboundOutcome.Resolved(InboundStage.NOT_A_NOTIFICATION, null)
        diagnostics.receiveStarted("notification-ind", subscriptionId)
        val binding = transports.assign(subscriptionId)
            ?: return InboundOutcome.Resolved(InboundStage.NO_TRANSPORT, null).also {
                diagnostics.receiveCompleted("no_transport", null)
            }
        val available = binding.transport.isAvailable(subscriptionId)
        diagnostics.transportSelected(binding.choice.id, subscriptionId, available)
        if (!available) {
            // The transport is configured but cannot run right now; tell the
            // carrier to retry rather than silently dropping the notification.
            binding.acknowledge(notification, HeaderField.STATUS_DEFERRED, subscriptionId)
            return InboundOutcome.Resolved(InboundStage.DEFERRED, null).also {
                diagnostics.receiveCompleted("deferred_unavailable", null)
            }
        }

        // A transport that runs the transaction through the platform has already
        // written this row and will rewrite it in place, so persisting it here
        // would file the message twice.
        val ownsRow = binding.ownsNotificationRow
        val announced = if (ownsRow) null else store.persist(notification, MmsBox.INBOX, subscriptionId)
        if (!ownsRow && announced == null) {
            binding.acknowledge(notification, HeaderField.STATUS_REJECTED, subscriptionId)
            return InboundOutcome.Resolved(InboundStage.NOT_STORED, null).also {
                diagnostics.receiveCompleted("not_stored", null)
            }
        }

        if (isExpired(notification)) {
            binding.acknowledge(notification, HeaderField.STATUS_EXPIRED, subscriptionId)
            return InboundOutcome.Resolved(InboundStage.EXPIRED, announced).also {
                diagnostics.receiveCompleted("expired", announced?.toString())
            }
        }

        if (!autoDownload.autoDownload(notification, subscriptionId)) {
            binding.acknowledge(notification, HeaderField.STATUS_DEFERRED, subscriptionId)
            return InboundOutcome.Resolved(InboundStage.DEFERRED, announced).also {
                diagnostics.receiveCompleted("deferred", announced?.toString())
            }
        }

        val listener = TransferListener()
        if (!binding.transport.retrieve(notification, subscriptionId, listener)) {
            binding.acknowledge(notification, HeaderField.STATUS_DEFERRED, subscriptionId)
            return InboundOutcome.Resolved(InboundStage.DEFERRED, announced).also {
                diagnostics.receiveCompleted("deferred_retrieve", announced?.toString())
            }
        }
        if (binding.reportsThroughPendingIntent) {
            return InboundOutcome.Resolved(InboundStage.AWAITING_PLATFORM, announced).also {
                diagnostics.receiveCompleted("awaiting_platform", announced?.toString())
            }
        }
        return awaitRetrieve(binding, announced, notification, subscriptionId, listener).also {
            diagnostics.receiveCompleted(it.stage.name, it.messageUri?.toString())
        }
    }

    private suspend fun awaitSend(outbox: Uri, listener: TransferListener): SendOutcome =
        when (val result = listener.awaitSend()) {
            is TransferResult.Answered -> {
                if (result.responseStatus == HeaderField.RESPONSE_STATUS_OK) {
                    SendOutcome.Sent(store.move(outbox, MmsBox.SENT) ?: outbox, result.responseStatus)
                } else {
                    store.move(outbox, MmsBox.FAILED)
                    SendOutcome.Failed(outbox, result.resultCodeOf(), result.httpStatus)
                }
            }

            is TransferResult.Failed -> {
                store.move(outbox, MmsBox.FAILED)
                SendOutcome.Failed(outbox, result.resultCode, result.httpStatus)
            }

            // A retrieve outcome cannot arrive on a send, so a transport that
            // made one ran a transaction it was not asked to run.
            is TransferResult.Retrieved -> {
                store.delete(outbox)
                SendOutcome.Rejected(SendRejection.NOT_STARTED)
            }
        }

    private suspend fun awaitRetrieve(
        binding: TransportBinding,
        announced: Uri?,
        notification: Pdu,
        subscriptionId: Int,
        listener: TransferListener,
    ): InboundOutcome.Resolved = when (val result = listener.awaitRetrieve()) {
        is TransferResult.Retrieved -> {
            val conf = result.pdu?.takeIf { it.isRetrieveConf && it.carriesContent }
            if (conf == null) {
                binding.acknowledge(notification, HeaderField.STATUS_REJECTED, subscriptionId)
                InboundOutcome.Resolved(InboundStage.FAILED, announced)
            } else {
                val stored = store.persist(conf, MmsBox.INBOX, subscriptionId)
                if (stored == null) {
                    binding.acknowledge(notification, HeaderField.STATUS_REJECTED, subscriptionId)
                    InboundOutcome.Resolved(InboundStage.NOT_STORED, announced)
                } else {
                    // Only once the content has a row of its own: deleting the
                    // announcement first would lose the message if this failed.
                    announced?.let { store.delete(it) }
                    binding.acknowledge(notification, HeaderField.STATUS_RETRIEVED, subscriptionId)
                    InboundOutcome.Resolved(InboundStage.RETRIEVED, stored)
                }
            }
        }

        is TransferResult.Failed -> {
            binding.acknowledge(notification, HeaderField.STATUS_REJECTED, subscriptionId)
            InboundOutcome.Resolved(InboundStage.FAILED, announced)
        }

        // A send outcome on a retrieve means the transport answered a transaction
        // it was not asked to run, so nothing was fetched.
        is TransferResult.Answered -> {
            binding.acknowledge(notification, HeaderField.STATUS_REJECTED, subscriptionId)
            InboundOutcome.Resolved(InboundStage.FAILED, announced)
        }
    }

    /**
     * Fits the attachment, budgeting the caption and the SMIL out of the
     * carrier's cap first.
     *
     * The budget comes from [BudgetPolicy] rather than from a subtraction here,
     * so the split between caption and attachment has one owner.
     */
    private fun fit(message: OutgoingMessage, profile: CarrierProfile): FitOutcome {
        val request = FitRequest(
            mimeType = message.attachmentMimeType,
            bytes = message.attachmentBytes,
            sourceWidth = message.sourceWidth,
            sourceHeight = message.sourceHeight,
            budgetBytes = BudgetPolicy.attachmentBudget(
                carrierMaxMessageSize = profile.maxMessageSize(),
                captionBytes = message.caption.toByteArray(Charsets.UTF_8).size,
                partCount = SendReqBuilder.partCount(message.caption),
            ),
            maxImageWidth = profile.maxImageWidth(),
            maxImageHeight = profile.maxImageHeight(),
            dimensionLimitsReported = profile.imageLimitsReported(),
        )
        val outcome = fitter.fit(request)
        // Reported here rather than inside the fitters: this is the only place
        // that knows the carrier profile the budget came from, so it is the only
        // place a picture can be traced from source pixels to sent bytes.
        when (outcome) {
            is FitOutcome.Fitted -> diagnostics.attachmentFitted(request, outcome)
            FitOutcome.Unreadable -> diagnostics.attachmentRejected(
                request.mimeType, request.bytes.size, request.budgetBytes, "unreadable",
            )
            FitOutcome.TooLarge -> diagnostics.attachmentRejected(
                request.mimeType, request.bytes.size, request.budgetBytes, "too_large",
            )
        }
        return outcome
    }

    private fun sendHeadersFor(subscriptionId: Int): SendHeaderPolicy = SendHeaderPolicy.withCarrierConfig(
        base = SendHeaderPolicy.DEFAULT,
        values = carrierConfig.load(subscriptionId) ?: MapCarrierValues(emptyMap()),
    )

    /**
     * Whether the carrier's own expiry has passed.
     *
     * A notification with no expiry is never expired: the field is mandatory on
     * the wire, but a value the parser could not resolve must not be read as
     * zero, which would expire every message it touched.
     */
    private fun isExpired(notification: Pdu): Boolean =
        notification.expirySeconds?.let { it in 1..nowSeconds() } == true

    /**
     * One transfer in flight, parked until the transport reports back.
     *
     * A transport calls its listener on whatever thread it finishes on, so the
     * only thing this does is hand the outcome to the suspending caller, which
     * then resumes on its own dispatcher before anything touches the store. A
     * second callback is dropped rather than resuming twice.
     */
    private class TransferListener : TransportListener {
        private val sendResult = CompletableDeferred<TransferResult>()
        private val retrieveResult = CompletableDeferred<TransferResult>()

        override fun onSendCompleted(responseStatus: Int?, httpStatus: Int) {
            sendResult.complete(TransferResult.Answered(responseStatus, httpStatus))
        }

        override fun onRetrieveCompleted(retrieveConf: Pdu?, httpStatus: Int) {
            retrieveResult.complete(TransferResult.Retrieved(retrieveConf, httpStatus))
        }

        override fun onFailed(resultCode: Int, httpStatus: Int) {
            sendResult.complete(TransferResult.Failed(resultCode, httpStatus))
            retrieveResult.complete(TransferResult.Failed(resultCode, httpStatus))
        }

        suspend fun awaitSend(): TransferResult = sendResult.await()

        suspend fun awaitRetrieve(): TransferResult = retrieveResult.await()
    }
}

/**
 * The result code a send's response status is recorded under.
 *
 * A transient response status means the MMSC will try again on its own behalf,
 * so the row stays retryable; a permanent one does not. A response the client
 * could not read at all is neither, and is recorded as unspecified rather than
 * as a network failure, because nothing failed on the network.
 */
private fun TransferResult.Answered.resultCodeOf(): Int = when (responseStatus) {
    HeaderField.RESPONSE_STATUS_OK -> MmsResultCode.OK.code
    in HeaderField.RESPONSE_STATUS_ERROR_TRANSIENT_FAILURE..HeaderField.RESPONSE_STATUS_ERROR_TRANSIENT_PARTIAL_SUCCESS ->
        MmsResultCode.RETRY.code
    else -> MmsResultCode.UNSPECIFIED.code
}