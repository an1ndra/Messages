package com.anindra.messages.ui

import com.anindra.messages.data.Message

/** Pure derivation of the "Message details" rows, kept out of the composable so
 *  the classification is unit tested. Localization happens in the UI. */
object MessageDetails {
    enum class Kind { SMS, MMS }
    enum class Direction { TO, FROM }
    enum class Status { SENDING, SENT, DELIVERED, RECEIVED, FAILED }

    fun kind(transport: String): Kind =
        if (transport.equals("mms", ignoreCase = true)) Kind.MMS else Kind.SMS

    fun direction(isMe: Boolean): Direction = if (isMe) Direction.TO else Direction.FROM

    fun status(status: String): Status = when (status) {
        "sending" -> Status.SENDING
        "delivered" -> Status.DELIVERED
        "received" -> Status.RECEIVED
        "failed" -> Status.FAILED
        else -> Status.SENT
    }

    /** Epoch millis of the delivery confirmation, or null when the message was
     *  never delivered (incoming, still sending, failed, or no report). */
    fun deliveredAt(message: Message): Long? =
        message.deliveredAt.takeIf { status(message.status) == Status.DELIVERED && it > 0 }

    fun kindLabel(kind: Kind): String = if (kind == Kind.MMS) "MMS" else "SMS"
}
