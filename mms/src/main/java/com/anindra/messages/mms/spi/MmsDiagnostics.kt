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

    /**
     * What the fitter decided, with the numbers behind the decision.
     *
     * A picture that arrives blurry is indistinguishable from one that was sent
     * blurry, and the cause is only visible here: whether the carrier reported a
     * dimension cap, how big the source was, what it was encoded to and whether
     * the byte budget or the pixel budget was the binding one.
     */
    fun attachmentFitted(request: FitRequest, outcome: FitOutcome.Fitted) = Unit

    /** The attachment could not be sent at all; [reason] names which limit failed. */
    fun attachmentRejected(
        mimeType: String,
        sourceBytes: Int,
        budgetBytes: Long,
        reason: String,
    ) = Unit

    /** A pending row was asked of the platform, and how hard it has been tried. */
    fun downloadRequested(rowId: Long, contentLocation: String?, attempt: Int) = Unit

    /**
     * The pending-row sweep ran and found [count] announced messages.
     *
     * Reported on every sweep, including when the answer is zero: "the sweep ran
     * and found nothing" is the one line that separates a carrier that sent
     * nothing from an app whose receive path never looked.
     */
    fun pendingSwept(count: Int) = Unit

    /**
     * A log line the app's own MMS code emitted.
     *
     * logcat is where MMS problems are first noticed but the worst place to read
     * them back: it is scrolled away, needs a cable, and cannot be asserted on.
     * Anything worth logging about MMS is therefore also recorded here, so the
     * same line is readable in Diagnostics as plain text.
     */
    fun noted(tag: String, level: String, message: String) = Unit

    /** A transfer finished; [httpStatus] is 0 when the platform reported none. */
    fun downloadCompleted(rowId: Long, resultCode: Int, httpStatus: Int) = Unit
}
