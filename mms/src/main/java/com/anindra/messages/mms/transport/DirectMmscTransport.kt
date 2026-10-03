package com.anindra.messages.mms.transport

import com.anindra.messages.mms.WspMmsCodec
import com.anindra.messages.mms.net.ApnProfile
import com.anindra.messages.mms.net.ApnResolver
import com.anindra.messages.mms.net.CarrierProfileStore
import com.anindra.messages.mms.net.HttpEngineFactory
import com.anindra.messages.mms.net.MmsNetworkGate
import com.anindra.messages.mms.net.MmscEndpoint
import com.anindra.messages.mms.net.MmscFailure
import com.anindra.messages.mms.net.MmscHttpClient
import com.anindra.messages.mms.net.MmscResponse
import com.anindra.messages.mms.net.toResultCode
import com.anindra.messages.mms.pdu.HeaderField
import com.anindra.messages.mms.pdu.MessageType
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.spi.MmsDiagnostics
import com.anindra.messages.mms.spi.MmsPduCodec
import com.anindra.messages.mms.spi.MmsTransport
import com.anindra.messages.mms.spi.NotificationAcknowledger
import com.anindra.messages.mms.spi.TransportListener
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Sends and retrieves by speaking HTTP to the MMSC ourselves.
 *
 * The sequence is the net layer's own: resolve the APN, take a lease on an
 * MMS-capable network, hand the request to `MmscHttpClient`, parse what comes
 * back. Two things are deliberately absent. There is no retry loop -- a
 * transport reports one outcome and the policy above it decides whether to try
 * again, so the two paths cannot disagree about what "failed" means. And there
 * is no fallback network: a null lease is `NO_NETWORK` and the request is never
 * issued, because sending an MMSC transaction over a network the carrier never
 * provisioned it for produces an opaque failure from the far side.
 *
 * Every failure is reported as the platform's own result code through
 * [MmscFailure.toResultCode], so the retry classification is the same whether
 * the bytes went through this transport or the platform's.
 */
class DirectMmscTransport(
    private val apnResolver: ApnResolver,
    private val networkGate: MmsNetworkGate,
    private val engineFactory: HttpEngineFactory,
    private val carrierProfiles: CarrierProfileStore,
    private val line1: (Int) -> String?,
    private val codec: MmsPduCodec = WspMmsCodec(),
    private val networkTimeoutMillis: Long = DEFAULT_NETWORK_TIMEOUT_MS,
    private val workers: Executor = Executors.newCachedThreadPool(),
    private val diagnostics: MmsDiagnostics = object : MmsDiagnostics {},
) : MmsTransport, NotificationAcknowledger {

    override val id: String = TransportChoice.DIRECT.id

    /** An APN with no usable MMSC is as close to "cannot send" as this layer can say. */
    override fun isAvailable(subscriptionId: Int): Boolean =
        apnResolver.resolve(subscriptionId)?.endpoint() != null

    override fun send(pdu: Pdu, subscriptionId: Int, listener: TransportListener): Boolean {
        val bytes = codec.compose(pdu) ?: return false
        val apn = apnResolver.resolve(subscriptionId)
            ?: return report(listener, MmscFailure.INVALID_APN).also {
                diagnostics.apnResolved(subscriptionId, null, null)
            }
        diagnostics.apnResolved(subscriptionId, apn.endpoint()?.url, apn.proxyLabel())
        workers.execute {
            when (val response = exchange(subscriptionId) { client ->
                client.post(bytes, apn, line1(subscriptionId))
            }) {
                is MmscResponse.Failure -> listener.onFailed(response.kind.toResultCode().code, statusOf(response))
                is MmscResponse.Success -> {
                    val conf = codec.parse(response.bytes)
                    listener.onSendCompleted(conf?.responseStatus, response.statusCode)
                }
            }
        }
        return true
    }

    override fun retrieve(notification: Pdu, subscriptionId: Int, listener: TransportListener): Boolean {
        val contentLocation = notification.contentLocation ?: return false
        val apn = apnResolver.resolve(subscriptionId)
            ?: return report(listener, MmscFailure.INVALID_APN).also {
                diagnostics.apnResolved(subscriptionId, null, null)
            }
        diagnostics.apnResolved(subscriptionId, apn.endpoint()?.url, apn.proxyLabel())
        workers.execute {
            when (val response = exchange(subscriptionId) { client ->
                client.retrieve(contentLocation, apn)
            }) {
                is MmscResponse.Failure -> listener.onFailed(response.kind.toResultCode().code, statusOf(response))
                // A body that does not parse is handed back as null rather than
                // raised: the caller has to decide what an unreadable Retrieve.conf
                // means for the row it was going to write.
                is MmscResponse.Success -> listener.onRetrieveCompleted(codec.parse(response.bytes), response.statusCode)
            }
        }
        return true
    }

    /**
     * Posts the M-NotifyResp.ind that closes an announced message's transaction.
     *
     * Nothing else sends it on this path, and a carrier that never sees one
     * re-delivers the notification, so this is the call the receive path makes on
     * every terminal branch.
     */
    override fun acknowledge(notification: Pdu, status: Int, subscriptionId: Int): Boolean {
        val transactionId = notification.transactionId?.takeIf { it.isNotBlank() } ?: return false
        val response = Pdu(MessageType.NOTIFYRESP_IND).apply {
            headers.setOctet(HeaderField.STATUS, status)
            headers.setText(HeaderField.TRANSACTION_ID, transactionId)
        }
        val bytes = codec.compose(response) ?: return false
        val apn = apnResolver.resolve(subscriptionId) ?: return false
        diagnostics.apnResolved(subscriptionId, apn.endpoint()?.url, apn.proxyLabel())
        workers.execute { exchange(subscriptionId) { client -> client.post(bytes, apn, line1(subscriptionId)) } }
        return true
    }

    /**
     * Runs one exchange on a network bound for MMS.
     *
     * The lease is checked for a handle as well as for existing: a lease whose
     * handle is null is unbound, and proceeding would put the request on the
     * default network, which for MMS is the wrong one.
     */
    private fun exchange(
        subscriptionId: Int,
        request: (MmscHttpClient) -> MmscResponse,
    ): MmscResponse {
        val lease = networkGate.acquire(networkTimeoutMillis)
        val handle = lease?.handle
        if (lease == null || handle == null) {
            lease?.close()
            diagnostics.networkResolved(false)
            return MmscResponse.Failure(MmscFailure.NO_NETWORK, null)
        }
        diagnostics.networkResolved(true)
        val carrier = carrierProfiles.of(subscriptionId)
        val client = MmscHttpClient(
            engine = engineFactory.create(handle),
            userAgent = { carrier.userAgent() },
            uaProfileUrl = { carrier.uaProfUrl() },
        )
        return lease.use { request(client) }
    }

    private fun ApnProfile.proxyLabel(): String? =
        (endpoint() as? MmscEndpoint.Proxied)?.proxy?.let { "${it.host}:${it.port}" }

    /**
     * A missing APN never reaches a network, so it is reported rather than thrown.
     *
     * Reported rather than returned as "did not start" because the result code is
     * the whole point: a carrier with no MMS APN is permanently unable to send,
     * and the retry policy above reads that from `MMS_DISABLED_BY_CARRIER`
     * rather than from a bare "not started".
     */
    private fun report(listener: TransportListener, failure: MmscFailure): Boolean {
        listener.onFailed(failure.toResultCode().code, 0)
        return true
    }

    private fun statusOf(response: MmscResponse.Failure): Int = response.statusCode ?: 0

    companion object {
        const val DEFAULT_NETWORK_TIMEOUT_MS = 10_000L
    }
}