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

    /** What a sent bubble's meta line says after the time. */
    enum class Meta { SENDING, DELIVERED, KIND }

    /**
     * The word after the time on one of your own messages.
     *
     * "Delivered" needs both the delivery-reports switch and a confirmed
     * delivery: the switch is what asked the network for the confirmation, so
     * without it the row can only ever be "sent" and the line falls back to the
     * message kind. Kept here rather than inline so the bubble and the details
     * sheet cannot drift apart, and so a switch that is off cannot be mistaken
     * for a lost delivery report.
     */
    fun meta(rawStatus: String, deliveryReports: Boolean): Meta = when {
        deliveryReports && status(rawStatus) == Status.DELIVERED -> Meta.DELIVERED
        rawStatus == "sending" -> Meta.SENDING
        else -> Meta.KIND
    }
}
