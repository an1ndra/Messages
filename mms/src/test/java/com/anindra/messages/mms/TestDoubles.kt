package com.anindra.messages.mms

import android.net.Uri
import com.anindra.messages.mms.net.MmsResultCode
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.spi.AttachmentFitter
import com.anindra.messages.mms.spi.FitOutcome
import com.anindra.messages.mms.spi.FitRequest
import com.anindra.messages.mms.spi.MmsTransport
import com.anindra.messages.mms.spi.NotificationAcknowledger
import com.anindra.messages.mms.spi.PlatformTransaction
import com.anindra.messages.mms.spi.TransportListener
import com.anindra.messages.mms.store.MmsBox
import com.anindra.messages.mms.store.MmsStore
import com.anindra.messages.mms.store.PendingMessage
import com.anindra.messages.mms.transport.TransportChoice
import java.io.InputStream

/**
 * An [MmsStore] over nothing but a list.
 *
 * It answers with parseable uris rather than nulls so a test can tell "stored
 * here" from "not stored" without holding a provider, and it records every
 * mutation the orchestration makes so the box a message ended in is assertable.
 */
class FakeMmsStore(
    private val acceptsPersist: Boolean = true,
) : MmsStore {
    val persists = mutableListOf<Triple<Pdu, MmsBox, Int>>()
    val moves = mutableListOf<Pair<String, MmsBox>>()
    val deletes = mutableListOf<String>()
    val pendingErrors = mutableListOf<Pair<String, Int>>()
    private val stored = mutableMapOf<String, Pdu>()
    private var nextId = 1L

    override fun persist(pdu: Pdu, box: MmsBox, subscriptionId: Int): Uri? {
        if (!acceptsPersist) return null
        persists += Triple(pdu, box, subscriptionId)
        val uri = uri("mms/fake/${nextId++}")
        stored[uri.toString()] = pdu
        return uri
    }

    override fun load(uri: Uri): Pdu? = stored[uri.toString()]

    override fun move(from: Uri, to: MmsBox): Uri? {
        moves += from.toString() to to
        val target = if (to.isAddressable) from.toString().replaceFirst("mms/", "mms/${to.path}/") else from.toString()
        return Uri.parse(target)
    }

    override fun updateMessageBox(uri: Uri, box: MmsBox): Boolean {
        moves += uri.toString() to box
        return true
    }

    override fun delete(uri: Uri): Boolean {
        deletes += uri.toString()
        stored.remove(uri.toString())
        return true
    }

    override fun setRead(uri: Uri, read: Boolean, seen: Boolean): Boolean = true

    override fun openPartStream(partUri: Uri): InputStream? = null

    override fun partBytes(uri: Uri): ByteArray? = null

    override fun createThreadId(recipients: List<String>): Long? = 1L

    override fun pendingMessages(dueBeforeSeconds: Long): List<PendingMessage> = emptyList()

    override fun setPendingErrorType(uri: Uri, errorType: Int): Boolean {
        pendingErrors += uri.toString() to errorType
        return true
    }

    override fun deletePending(uri: Uri): Boolean = true

    private fun uri(path: String) = Uri.parse("content://$path")

    /** The message persisted into [box] last, or null when none was. */
    fun persistedIn(box: MmsBox): Pdu? = persists.lastOrNull { it.second == box }?.first
}

/** A fitter that answers from a script and remembers every request it was given. */
class RecordingFitter(private val outcome: (FitRequest) -> FitOutcome) : AttachmentFitter {
    val requests = mutableListOf<FitRequest>()

    override fun fit(request: FitRequest): FitOutcome {
        requests += request
        return outcome(request)
    }

    companion object {
        fun fitting(bytes: ByteArray, mimeType: String = "image/jpeg") =
            RecordingFitter { FitOutcome.Fitted(bytes, mimeType, 0, 0) }
    }
}

/**
 * A transport whose every answer is scripted.
 *
 * The capability flags are constructor arguments rather than traits, because
 * they are exactly what decides whether the orchestration waits for the listener
 * or persists the row itself -- a fake that hardcoded them would test the fake.
 */
class ScriptedTransport(
    override val id: String = TransportChoice.DIRECT.id,
    private val available: Boolean = true,
    override val reportsThroughPendingIntent: Boolean = false,
    override val ownsNotificationRow: Boolean = false,
    private val onSend: (Pdu, TransportListener) -> Unit = { _, listener ->
        listener.onFailed(MmsResultCode.IO_ERROR.code, 0)
    },
    private val onRetrieve: (Pdu, TransportListener) -> Unit = { _, listener ->
        listener.onFailed(MmsResultCode.IO_ERROR.code, 0)
    },
    private val starts: Boolean = true,
) : MmsTransport, NotificationAcknowledger, PlatformTransaction {

    val sends = mutableListOf<Pdu>()
    val retrieves = mutableListOf<Pdu>()
    val acknowledgements = mutableListOf<Pair<String, Int>>()

    override fun isAvailable(subscriptionId: Int): Boolean = available

    // A transport that reports through a PendingIntent never calls the listener,
    // which is the behaviour the orchestration has to know about.
    override fun send(pdu: Pdu, subscriptionId: Int, listener: TransportListener): Boolean {
        if (!starts) return false
        sends += pdu
        if (!reportsThroughPendingIntent) onSend(pdu, listener)
        return true
    }

    override fun retrieve(notification: Pdu, subscriptionId: Int, listener: TransportListener): Boolean {
        if (!starts) return false
        retrieves += notification
        if (!reportsThroughPendingIntent) onRetrieve(notification, listener)
        return true
    }

    override fun acknowledge(notification: Pdu, status: Int, subscriptionId: Int): Boolean {
        acknowledgements += notification.transactionId.orEmpty() to status
        return true
    }

    /** The status answered for [transactionId], or null when none was. */
    fun statusAnsweredFor(transactionId: String): Int? =
        acknowledgements.lastOrNull { it.first == transactionId }?.second
}

