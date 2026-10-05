package com.anindra.messages.mms.spi

import com.anindra.messages.mms.pdu.Pdu

/**
 * The provider row an announced message's content is fetched into.
 *
 * `MmsTransport.retrieve` takes only the notification, but the platform download
 * API is handed the destination row and rewrites it in place, so the transport
 * has to be told which row that is. It is injected rather than added to
 * [MmsTransport] because only the platform transport has a destination to name:
 * a transport that speaks to the MMSC over HTTP hands the PDU back and lets the
 * caller persist it.
 */
fun interface MmsDownloadTarget {
    /** The `content://` uri for [notification], or null when there is no such row. */
    fun contentUriOf(notification: Pdu, subscriptionId: Int): String?
}