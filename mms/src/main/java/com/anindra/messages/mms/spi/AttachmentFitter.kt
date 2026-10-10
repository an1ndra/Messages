package com.anindra.messages.mms.spi

/**
 * Fits an attachment to what the carrier will accept.
 *
 * Split from the codec because the failure modes are user-facing and distinct:
 * an attachment that cannot be read and one that is too large must not produce
 * the same outcome, or the UI cannot tell the user which happened.
 */
interface AttachmentFitter {
    fun fit(request: FitRequest): FitOutcome
}

data class FitRequest(
    val mimeType: String,
    /** Raw bytes and the dimensions already known, or null when not yet measured. */
    val bytes: ByteArray,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    /** Octets available for the attachment once the headers, SMIL and text are paid for. */
    val budgetBytes: Long,
    val maxImageWidth: Int,
    val maxImageHeight: Int,
    /**
     * Whether [maxImageWidth]/[maxImageHeight] are the carrier's own limits.
     *
     * False means the numbers are the fallback guess, not a restriction: the
     * fitter must then treat the byte budget as the only limit, because cutting
     * a photo to a size nobody asked for is what makes it arrive blurry.
     */
    val dimensionLimitsReported: Boolean = false,
)

sealed interface FitOutcome {
    /** [bytes] may have been re-encoded; [mimeType] is what the bytes now are. */
    data class Fitted(val bytes: ByteArray, val mimeType: String, val width: Int, val height: Int) : FitOutcome

    /** The attachment could not be read or decoded at all. */
    data object Unreadable : FitOutcome

    /** Readable, but it cannot be made to fit even after downscaling. */
    data object TooLarge : FitOutcome
}

/** Largest part of the budget the text body may claim, leaving room for SMIL and headers. */
const val MMS_TEXT_BUDGET_FRACTION = 0.25

/**
 * Splits the message budget between the attachment and its caption.
 *
 * Pure so the arithmetic is testable without Android. The split is deliberately
 * biased: a caption is optional and an attachment is the point of the message, so
 * on a tight budget the attachment keeps the larger share.
 */
object BudgetPolicy {
    /**
     * Headroom for the SMIL part, the header block and multipart framing, so an
     * attachment that exactly fills its share still leaves a PDU under the cap.
     */
    const val PDU_OVERHEAD_BYTES: Long = 12 * 1024

    fun attachmentBudget(
        carrierMaxMessageSize: Int,
        captionBytes: Int,
        partCount: Int,
    ): Long {
        val total = carrierMaxMessageSize.toLong() - PDU_OVERHEAD_BYTES
        if (total <= 0) return 0
        val textShare = (captionBytes.toLong() * MMS_TEXT_BUDGET_FRACTION).toLong() + partCount * 512
        return (total - textShare).coerceAtLeast(0)
    }
}
