package com.anindra.messages.mms.spi

/**
 * A debug surface for the MMS stack.
 *
 * The methods are no-ops by default so production callers can pass an instance
 * only when they want one, and the interface stays free of android.* types so it
 * lives in the same testable layer as the codec seams.
 */
interface MmsDiagnostics {
    fun sendStarted(transportId: String, subscriptionId: Int, messageUri: String?) = Unit
    fun sendBuilt(pduSize: Int, recipientCount: Int) = Unit
    fun sendCompleted(
        transportId: String,
        outcome: String,
        responseStatus: Int?,
        httpStatus: Int,
    ) = Unit

    fun receiveStarted(messageType: String, subscriptionId: Int) = Unit
    fun receiveCompleted(stage: String, messageUri: String?) = Unit
    fun transportSelected(transportId: String, subscriptionId: Int, available: Boolean) = Unit
    fun apnResolved(subscriptionId: Int, mmsc: String?, proxy: String?) = Unit
    fun networkResolved(available: Boolean) = Unit
}
