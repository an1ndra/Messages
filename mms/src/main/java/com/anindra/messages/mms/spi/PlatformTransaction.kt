package com.anindra.messages.mms.spi

/**
 * Declares that a transport runs a transaction through the platform and reports
 * it back through the app's own PendingIntent.
 *
 * `sendMultimediaMessage` and `downloadMultimediaMessage` are both handed their
 * completion PendingIntents as arguments and fill in the result themselves, so
 * there is nothing for [MmsTransport]'s listener to be called with. The download
 * additionally rewrites the destination row in place, so the caller must not
 * persist the announcement or the retrieved PDU either. A transport that speaks
 * to the MMSC over HTTP does none of those four things: it calls the listener,
 * hands the PDU back to be stored, and has to be given the M-NotifyResp.ind.
 *
 * The two facts are declared together because they are true together, and a
 * transport that answered one and not the other would either suspend forever
 * waiting for a callback that never comes or file every incoming message twice.
 */
interface PlatformTransaction {
    /** The outcome reaches the app as a broadcast; the listener never fires. */
    val reportsThroughPendingIntent: Boolean

    /** The download rewrote the destination row; the caller must not persist it again. */
    val ownsNotificationRow: Boolean
}