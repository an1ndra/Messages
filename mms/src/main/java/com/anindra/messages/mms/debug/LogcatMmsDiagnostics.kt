package com.anindra.messages.mms.debug

import android.util.Log
import com.anindra.messages.mms.spi.FitOutcome
import com.anindra.messages.mms.spi.FitRequest
import com.anindra.messages.mms.spi.MmsDiagnostics

/**
 * Mirrors every [MmsDiagnostics] event to logcat and passes it on.
 *
 * The recorder alone only lives as long as the process and is only readable
 * from one screen; logcat is what survives a crash, a restart and a bug report,
 * so the same events go to both.
 *
 * Nothing here formats a recipient number, a message body or a caption: an event
 * carries ids, sizes and result codes only, because this text ends up in logcat
 * and in whatever the user attaches to a bug report.
 */
class LogcatMmsDiagnostics(
    private val delegate: MmsDiagnostics,
    private val tag: String = TAG,
) : MmsDiagnostics {

    override fun sendStarted(transportId: String, subscriptionId: Int, messageUri: String?) {
        log("send started transport=$transportId sub=$subscriptionId uri=${messageUri ?: "-"}")
        delegate.sendStarted(transportId, subscriptionId, messageUri)
    }

    override fun sendBuilt(pduSize: Int, recipientCount: Int) {
        log("send built pdu=${pduSize}B recipients=$recipientCount")
        delegate.sendBuilt(pduSize, recipientCount)
    }

    override fun sendCompleted(
        transportId: String,
        outcome: String,
        responseStatus: Int?,
        httpStatus: Int,
    ) {
        log(
            "send completed transport=$transportId outcome=$outcome " +
                "responseStatus=${responseStatus?.toString() ?: "-"} httpStatus=$httpStatus"
        )
        delegate.sendCompleted(transportId, outcome, responseStatus, httpStatus)
    }

    override fun receiveStarted(messageType: String, subscriptionId: Int) {
        log("receive started type=$messageType sub=$subscriptionId")
        delegate.receiveStarted(messageType, subscriptionId)
    }

    override fun receiveCompleted(stage: String, messageUri: String?) {
        log("receive completed stage=$stage uri=${messageUri ?: "-"}")
        delegate.receiveCompleted(stage, messageUri)
    }

    override fun transportSelected(transportId: String, subscriptionId: Int, available: Boolean) {
        log("transport $transportId sub=$subscriptionId available=$available")
        delegate.transportSelected(transportId, subscriptionId, available)
    }

    override fun apnResolved(subscriptionId: Int, mmsc: String?, proxy: String?) {
        log("apn sub=$subscriptionId mmsc=${mmsc ?: "-"} proxy=${proxy ?: "-"}")
        delegate.apnResolved(subscriptionId, mmsc, proxy)
    }

    override fun networkResolved(available: Boolean) {
        log("network available=$available")
        delegate.networkResolved(available)
    }

    override fun attachmentFitted(request: FitRequest, outcome: FitOutcome.Fitted) {
        log(
            "fit ok ${request.sourceWidth}x${request.sourceHeight}/${request.bytes.size}B " +
                "-> ${outcome.width}x${outcome.height}/${outcome.bytes.size}B " +
                "budget=${request.budgetBytes}B cap=${request.maxImageWidth}x${request.maxImageHeight}"
        )
        delegate.attachmentFitted(request, outcome)
    }

    override fun attachmentRejected(
        mimeType: String,
        sourceBytes: Int,
        budgetBytes: Long,
        reason: String,
    ) {
        log("fit rejected mime=$mimeType source=${sourceBytes}B budget=${budgetBytes}B reason=$reason")
        delegate.attachmentRejected(mimeType, sourceBytes, budgetBytes, reason)
    }

    override fun downloadRequested(rowId: Long, contentLocation: String?, attempt: Int) {
        log("download requested row=$rowId attempt=$attempt location=${contentLocation?.takeIf { it.isNotBlank() } ?: "-"}")
        delegate.downloadRequested(rowId, contentLocation, attempt)
    }

    override fun pendingSwept(count: Int) {
        log("download sweep found $count pending MMS")
        delegate.pendingSwept(count)
    }

    override fun noted(tag: String, level: String, message: String) {
        // Kept under the tag the line was written with, so a grep for the
        // component still finds it; MmsTrace is only the roll-up stream.
        when (level) {
            "w" -> Log.w(tag, message)
            "e" -> Log.e(tag, message)
            else -> Log.i(tag, message)
        }
        delegate.noted(tag, level, message)
    }

    override fun downloadCompleted(rowId: Long, resultCode: Int, httpStatus: Int) {
        log("download completed row=$rowId resultCode=$resultCode httpStatus=$httpStatus")
        delegate.downloadCompleted(rowId, resultCode, httpStatus)
    }

    private fun log(message: String) = Log.i(tag, message)

    companion object {
        const val TAG = "MmsTrace"
    }
}