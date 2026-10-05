package com.anindra.messages.mms.transport

/**
 * Records every call it received and every callback the transport handed it.
 *
 * The callbacks are what a real `PendingIntent` would have been built from, so
 * the flags on them are the flags the transport chose -- which is the only way to
 * assert them without a device.
 */
class RecordingMmsPlatform(private val available: Boolean = true) : MmsPlatform {
    val callbacks = mutableListOf<MmsCallback>()
    val sends = mutableListOf<Send>()
    val downloads = mutableListOf<Download>()

    data class Send(
        val subscriptionId: Int,
        val locationUri: String,
        val configOverrides: Map<String, Any>,
        val sent: MmsCallback,
    )

    data class Download(
        val subscriptionId: Int,
        val locationUrl: String,
        val contentUri: String,
        val configOverrides: Map<String, Any>,
        val completion: MmsCallback,
    )

    override fun canSend(subscriptionId: Int): Boolean = available

    override fun sendMultimedia(
        subscriptionId: Int,
        locationUri: String,
        configOverrides: Map<String, Any>,
        sent: MmsCallback,
    ) {
        sends += Send(subscriptionId, locationUri, configOverrides, sent)
        callbacks += sent
    }

    override fun downloadMultimedia(
        subscriptionId: Int,
        locationUrl: String,
        contentUri: String,
        configOverrides: Map<String, Any>,
        completion: MmsCallback,
    ) {
        downloads += Download(subscriptionId, locationUrl, contentUri, configOverrides, completion)
        callbacks += completion
    }
}

/** A listener that answers nothing, for the transports that report by PendingIntent. */
object SilentTransportListener : com.anindra.messages.mms.spi.TransportListener {
    override fun onSendCompleted(responseStatus: Int?, httpStatus: Int) = Unit
    override fun onRetrieveCompleted(retrieveConf: com.anindra.messages.mms.pdu.Pdu?, httpStatus: Int) = Unit
    override fun onFailed(resultCode: Int, httpStatus: Int) = Unit
}