package com.anindra.messages.mms.fit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.anindra.messages.mms.spi.AttachmentFitter
import com.anindra.messages.mms.spi.FitOutcome
import com.anindra.messages.mms.spi.FitRequest
import java.io.ByteArrayOutputStream

/**
 * Fits an image attachment to the carrier's size and dimension limits.
 *
 * Downscale and re-encode happen together because either alone is insufficient:
 * shrinking dimensions does not guarantee the byte count falls under the cap, and
 * lowering quality cannot rescue dimensions the carrier rejects outright.
 */
class ImageAttachmentFitter(
    private val qualities: IntArray = DEFAULT_QUALITIES,
) : AttachmentFitter {

    override fun fit(request: FitRequest): FitOutcome {
        if (request.budgetBytes <= 0) return FitOutcome.TooLarge

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(request.bytes, 0, request.bytes.size, bounds)
        val sourceWidth = bounds.outWidth
        val sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) return FitOutcome.Unreadable

        val target = ImageSizing.fitWithin(
            sourceWidth, sourceHeight, request.maxImageWidth, request.maxImageHeight,
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageSizing.sampleSize(sourceWidth, sourceHeight, target)
        }
        val decoded = BitmapFactory.decodeByteArray(request.bytes, 0, request.bytes.size, options)
            ?: return FitOutcome.Unreadable

        // Subsampling only lands on powers of two, so it cannot by itself hit the
        // cap; the exact resize to the target has to happen explicitly.
        val scaled = target?.let {
            if (decoded.width == it.width && decoded.height == it.height) decoded
            else Bitmap.createScaledBitmap(decoded, it.width, it.height, true)
        } ?: decoded

        return try {
            for (quality in qualities) {
                val out = ByteArrayOutputStream()
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    return FitOutcome.Unreadable
                }
                if (out.size().toLong() <= request.budgetBytes) {
                    // Downscaling may have turned a PNG or WebP into a JPEG, so the
                    // part has to be typed from the bytes actually being sent.
                    return FitOutcome.Fitted(
                        bytes = out.toByteArray(),
                        mimeType = JPEG,
                        width = scaled.width,
                        height = scaled.height,
                    )
                }
            }
            FitOutcome.TooLarge
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    companion object {
        const val JPEG = "image/jpeg"

        /** Descending ladder: the first entry that fits is used. */
        val DEFAULT_QUALITIES = intArrayOf(90, 75, 60, 45)
    }
}
