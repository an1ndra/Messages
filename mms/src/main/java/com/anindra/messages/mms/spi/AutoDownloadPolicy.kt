package com.anindra.messages.mms.spi

import com.anindra.messages.mms.pdu.Pdu

/**
 * Whether an announced message is fetched over the cellular network now or left
 * for the user to open.
 *
 * Kept out of the receive path as a decision so it can be tested without a
 * network: every branch of [com.anindra.messages.mms.Mms.receive] is the same
 * whichever answer this gives, only the notification status and the timing
 * differ.
 */
fun interface AutoDownloadPolicy {
    fun autoDownload(notification: Pdu, subscriptionId: Int): Boolean
}