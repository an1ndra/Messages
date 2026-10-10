package com.anindra.messages.data

/**
 * Per-subscription MMS parameters resolved from the platform carrier config.
 *
 * The send path previously hardcoded all of this, which silently misbehaves on a
 * carrier that caps message size or image dimensions: an oversized attachment is
 * composed and handed to the radio, which then rejects it with no way for the app
 * to explain why.
 *
 * The [KEY_*] names mirror the public `CarrierConfigManager.KEY_MMS_*` constants
 * and are spelled out here so this file stays free of android imports and can be
 * exercised directly by unit tests.
 *
 * Only the values the send path actually enforces are modelled. Recipient limits
 * and the SMS-to-MMS promotion thresholds are applied by the platform from the
 * overrides bundle, so duplicating them here would be a second source of truth
 * that can disagree with it.
 */
data class MmsConfig(
    val maxMessageSize: Int = DEFAULT_MAX_MESSAGE_SIZE,
    val maxImageWidth: Int = DEFAULT_MAX_IMAGE_WIDTH,
    val maxImageHeight: Int = DEFAULT_MAX_IMAGE_HEIGHT,
    val notifyWapMmsc: Boolean = false,
    val deliveryReport: Boolean = false,
    val readReport: Boolean = false
) {
    /** A carrier cap must be enforced before the PDU reaches the radio, not after. */
    fun acceptsPayload(bytes: Long): Boolean = bytes in 0..maxMessageSize.toLong()

    fun deliveryReportHeader(): Int = if (deliveryReport) YES else NO

    fun readReportHeader(): Int = if (readReport) YES else NO

    companion object {
        /** `PduHeaders.VALUE_YES` / `VALUE_NO`, repeated to keep this file android-free. */
        const val YES = 0x80
        const val NO = 0x81

        const val DEFAULT_MAX_MESSAGE_SIZE = 307_200
        const val DEFAULT_MAX_IMAGE_WIDTH = 640
        const val DEFAULT_MAX_IMAGE_HEIGHT = 480

        const val KEY_MAX_MESSAGE_SIZE = "maxMessageSize"
        const val KEY_MAX_IMAGE_WIDTH = "maxImageWidth"
        const val KEY_MAX_IMAGE_HEIGHT = "maxImageHeight"
        const val KEY_NOTIFY_WAP_MMSC = "enabledNotifyWapMMSC"
        const val KEY_DELIVERY_REPORT = "enableMMSDeliveryReports"
        const val KEY_READ_REPORT = "enableMMSReadReports"

        /** Carriers that omit a value fall back to the AOSP `mms_config.xml` baseline. */
        fun from(values: CarrierValues): MmsConfig = MmsConfig(
            maxMessageSize = values.integer(KEY_MAX_MESSAGE_SIZE, DEFAULT_MAX_MESSAGE_SIZE)
                .coerceAtLeast(0),
            maxImageWidth = values.integer(KEY_MAX_IMAGE_WIDTH, DEFAULT_MAX_IMAGE_WIDTH),
            maxImageHeight = values.integer(KEY_MAX_IMAGE_HEIGHT, DEFAULT_MAX_IMAGE_HEIGHT),
            notifyWapMmsc = values.boolean(KEY_NOTIFY_WAP_MMSC, false),
            deliveryReport = values.boolean(KEY_DELIVERY_REPORT, false),
            readReport = values.boolean(KEY_READ_REPORT, false)
        )
    }
}

/** Carrier config lookup, narrowed to what [MmsConfig] needs so it can be faked. */
interface CarrierValues {
    fun boolean(key: String, fallback: Boolean): Boolean
    fun integer(key: String, fallback: Int): Int
}
