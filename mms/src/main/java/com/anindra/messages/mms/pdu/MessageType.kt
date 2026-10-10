package com.anindra.messages.mms.pdu

/**
 * X-Mms-Message-Type values.
 *
 * Only the nine this app can build or interpret are named. The Mbox/Forward/
 * Delete/Cancel range above 0x88 is legal on the wire and is deliberately
 * unnamed: those transactions are stored-only server operations, and naming them
 * would invite code that pretends to handle them.
 */
object MessageType {
    const val SEND_REQ = 0x80
    const val SEND_CONF = 0x81
    const val NOTIFICATION_IND = 0x82
    const val NOTIFYRESP_IND = 0x83
    const val RETRIEVE_CONF = 0x84
    const val ACKNOWLEDGE_IND = 0x85
    const val DELIVERY_IND = 0x86
    const val READ_REC_IND = 0x87
    const val READ_ORIG_IND = 0x88

    const val FIRST_SUPPORTED = SEND_REQ
    const val LAST_SUPPORTED = READ_ORIG_IND

    fun isSupported(messageType: Int): Boolean = messageType in FIRST_SUPPORTED..LAST_SUPPORTED

    fun name(messageType: Int): String = when (messageType) {
        SEND_REQ -> "M-Send.req"
        SEND_CONF -> "M-Send.conf"
        NOTIFICATION_IND -> "M-Notification.ind"
        NOTIFYRESP_IND -> "M-NotifyResp.ind"
        RETRIEVE_CONF -> "M-Retrieve.conf"
        ACKNOWLEDGE_IND -> "M-Acknowledge.ind"
        DELIVERY_IND -> "M-Delivery.ind"
        READ_REC_IND -> "M-ReadRec.ind"
        READ_ORIG_IND -> "M-ReadOrig.ind"
        else -> "M-Unknown.0x%02X".format(messageType)
    }

    /** True for the two types that carry a multipart body. */
    fun hasBody(messageType: Int): Boolean =
        messageType == SEND_REQ || messageType == RETRIEVE_CONF
}
