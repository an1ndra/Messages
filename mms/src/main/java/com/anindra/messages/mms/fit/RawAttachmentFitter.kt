package com.anindra.messages.mms.fit

import com.anindra.messages.mms.pdu.ContentTypes
import com.anindra.messages.mms.spi.AttachmentFitter
import com.anindra.messages.mms.spi.FitOutcome
import com.anindra.messages.mms.spi.FitRequest

/**
 * Fits a non-image attachment.
 *
 * The bytes pass through unchanged because there is nothing to re-encode, but the
 * stream is still bounded: an unbounded read is how a large video exhausts memory
 * and takes the process with it. Exceeding the budget is reported rather than
 * truncated, because a silently shortened attachment is worse than a clear
 * failure.
 */
class RawAttachmentFitter : AttachmentFitter {
    override fun fit(request: FitRequest): FitOutcome {
        if (request.bytes.isEmpty()) return FitOutcome.Unreadable
        if (request.budgetBytes <= 0) return FitOutcome.TooLarge
        if (request.bytes.size.toLong() > request.budgetBytes) return FitOutcome.TooLarge
        return FitOutcome.Fitted(
            bytes = request.bytes,
            mimeType = ContentTypes.normalize(request.mimeType),
            width = 0,
            height = 0,
        )
    }
}
