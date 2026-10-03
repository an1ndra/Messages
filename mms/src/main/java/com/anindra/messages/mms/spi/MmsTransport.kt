package com.anindra.messages.mms.spi

import com.anindra.messages.mms.pdu.Pdu

/**
 * Hands a composed message to the network.
 *
 * Two implementations exist and both ship: [id] is what Diagnostics reports, so a
 * carrier that behaves differently on one path can be switched without a code
 * change. Everything above this interface is transport-agnostic — that is the
 * point of it.
 */
interface MmsTransport {
    val id: String

    /**
     * Whether this transport can run for [subscriptionId] right now. A transport
     * that is merely idle should report true and fail the call, not report false
     * and be silently skipped.
     */
    fun isAvailable(subscriptionId: Int): Boolean

    /**
     * Sends [pdu]. Returns false only when the send could not be started at all;
     * a send that started and then failed arrives at [listener] instead.
     */
    fun send(pdu: Pdu, subscriptionId: Int, listener: TransportListener): Boolean

    /** Fetches the content announced by an M-Notification.ind. */
    fun retrieve(notification: Pdu, subscriptionId: Int, listener: TransportListener): Boolean
}

/**
 * Outcome callbacks. Every method may be called on a binder or worker thread, so
 * an implementation must marshal before touching UI or the app database.
 */
interface TransportListener {
    /** The MMSC answered a send. [responseStatus] is the X-Mms-Response-Status octet. */
    fun onSendCompleted(responseStatus: Int?, httpStatus: Int)

    /** The MMSC answered a retrieve. [retrieveConf] is null when the body was unusable. */
    fun onRetrieveCompleted(retrieveConf: Pdu?, httpStatus: Int)

    /**
     * The attempt failed. [resultCode] is an `SmsManager.MMS_ERROR_*` value so
     * the existing retry classification applies unchanged.
     */
    fun onFailed(resultCode: Int, httpStatus: Int)
}
