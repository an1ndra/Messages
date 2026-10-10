package com.anindra.messages.mms.store

import android.net.Uri
import android.provider.Telephony
import com.anindra.messages.mms.pdu.Pdu
import java.io.InputStream

/**
 * Storage for MMS messages in the telephony provider.
 *
 * This is the only surface that talks to `content://mms`, so nothing above it has
 * to know the provider's table and column layout.
 */
interface MmsStore {

    /** Writes [pdu] and everything hanging off it, returning the stored message's uri. */
    fun persist(pdu: Pdu, box: MmsBox, subscriptionId: Int): Uri?

    fun load(uri: Uri): Pdu?

    /** Repoints [from] at [to] and returns the uri in its new box. */
    fun move(from: Uri, to: MmsBox): Uri?

    fun updateMessageBox(uri: Uri, box: MmsBox): Boolean

    fun delete(uri: Uri): Boolean

    fun setRead(uri: Uri, read: Boolean, seen: Boolean): Boolean

    fun openPartStream(partUri: Uri): InputStream?

    fun partBytes(uri: Uri): ByteArray?

    /** The conversation a set of participants belongs to, creating it if needed. */
    fun createThreadId(recipients: List<String>): Long?

    /** Messages the provider still owes us a send or download for, oldest first. */
    fun pendingMessages(dueBeforeSeconds: Long): List<PendingMessage>

    fun setPendingErrorType(uri: Uri, errorType: Int): Boolean

    fun deletePending(uri: Uri): Boolean
}

/**
 * The provider's `msg_box` values.
 *
 * The numbers come from `Telephony.Mms.MESSAGE_BOX_*` rather than being restated,
 * so they cannot drift from the platform. Note that 5 is *failed* — there is no
 * temporary box in the SDK, and treating 5 as one silently marks a message as
 * failed.
 *
 * [path] is the URI segment the box is addressed by, and is null for the two that
 * have none: ALL is a query filter rather than a place, and FAILED is only ever
 * reached by updating `msg_box` on a row that already exists.
 */
enum class MmsBox(val value: Int, val path: String?) {
    ALL(Telephony.Mms.MESSAGE_BOX_ALL, null),
    INBOX(Telephony.Mms.MESSAGE_BOX_INBOX, "inbox"),
    SENT(Telephony.Mms.MESSAGE_BOX_SENT, "sent"),
    DRAFTS(Telephony.Mms.MESSAGE_BOX_DRAFTS, "drafts"),
    OUTBOX(Telephony.Mms.MESSAGE_BOX_OUTBOX, "outbox"),
    FAILED(Telephony.Mms.MESSAGE_BOX_FAILED, null),
    ;

    /** False for the boxes with no collection URI, so a caller cannot build a bad one. */
    val isAddressable: Boolean get() = path != null

    companion object {
        fun of(value: Int): MmsBox? = entries.firstOrNull { it.value == value }

        /** Boxes a message can be persisted into. */
        val persistable: List<MmsBox> = entries.filter { it.isAddressable }
    }
}

/** One row of `content://mms-sms/pending`, joined with the message it refers to. */
data class PendingMessage(
    val uri: Uri,
    val messageId: Long,
    val transactionId: String?,
    val messageSize: Long,
    val dueTimeSeconds: Long,
    val errorType: Int,
    val retryIndex: Int,
)
