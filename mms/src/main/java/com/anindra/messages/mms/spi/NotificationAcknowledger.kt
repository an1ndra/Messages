package com.anindra.messages.mms.spi

import com.anindra.messages.mms.pdu.Pdu

/**
 * Sends the M-NotifyResp.ind that ends an announced message's transaction.
 *
 * [MmsTransport] does not carry it because it is not every transport's to send.
 * `downloadMultimediaMessage` answers the notification itself, before it
 * retrieves, so a second response from us would be a duplicate transaction the
 * carrier answers twice. A transport that fetches over HTTP has nobody else
 * doing it, and an unanswered notification is re-delivered by the carrier, so
 * that transport implements this instead.
 *
 * Every terminal branch of the receive path must call this: retrieved,
 * deferred, rejected or expired. A branch that returns without answering is the
 * defect this interface exists to make impossible to write quietly.
 */
interface NotificationAcknowledger {

    /** Posts an M-NotifyResp.ind carrying [status]; false when none was started. */
    fun acknowledge(notification: Pdu, status: Int, subscriptionId: Int): Boolean
}