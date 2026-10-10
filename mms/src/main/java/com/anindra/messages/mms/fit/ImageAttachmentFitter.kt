package com.anindra.messages.mms.fit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.anindra.messages.mms.spi.AttachmentFitter
import com.anindra.messages.mms.spi.FitOutcome
import com.anindra.messages.mms.spi.FitRequest
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Fits an image attachment to the carrier's size and dimension limits.
 *
 * Downscale and re-encode happen together because either alone is insufficient:
 * shrinking dimensions does not guarantee the byte count falls under the cap, and
 * lowering quality cannot rescue dimensions the carrier rejects outright.
 *
 * Which of the two binds depends on what the carrier actually said. When it
 * declared image bounds they are a hard limit and the picture is resized to
 * them. When it declared nothing, the 640x480 in [FitRequest] is a guess, and
 * obeying it is what made every photo arrive soft — so the bytes are the only
 * limit and the search spends quality first, resolution last.
 */
class ImageAttachmentFitter(
    private val qualities: IntArray = DEFAULT_QUALITIES,
    private val firstQualities: IntArray = FIRST_QUALITIES,
    private val sizeSteps: DoubleArray = SIZE_STEPS,
) : AttachmentFitter {

    override fun fit(request: FitRequest): FitOutcome {
        if (request.budgetBytes <= 0) return FitOutcome.TooLarge

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(request.bytes, 0, request.bytes.size, bounds)
        val sourceWidth = bounds.outWidth
        val sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) return FitOutcome.Unreadable

        val cap = if (request.dimensionLimitsReported) {
            ImageSizing.fitWithin(
                sourceWidth, sourceHeight, request.maxImageWidth, request.maxImageHeight,
            )
        } else {
            ImageSizing.fitWithin(sourceWidth, sourceHeight, MAX_UNREPORTED_EDGE, MAX_UNREPORTED_EDGE)
        }
        val startWidth = cap?.width ?: sourceWidth
        val startHeight = cap?.height ?: sourceHeight
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageSizing.sampleSize(sourceWidth, sourceHeight, cap)
        }
        val decoded = BitmapFactory.decodeByteArray(request.bytes, 0, request.bytes.size, options)
            ?: return FitOutcome.Unreadable

        var current: Bitmap? = null
        var currentWidth = -1
        var currentHeight = -1
        return try {
            for ((width, height, quality) in searchPlan(startWidth, startHeight, sizeSteps, firstQualities, qualities)) {
                // Consecutive steps usually share a size after rounding; rescaling
                // for each quality would allocate the same bitmap twice.
                val candidate = if (decoded.width == width && decoded.height == height) {
                    decoded
                } else if (currentWidth == width && currentHeight == height && current != null) {
                    current!!
                } else {
                    current?.recycle()
                    Bitmap.createScaledBitmap(decoded, width, height, true).also {
                        current = it
                        currentWidth = width
                        currentHeight = height
                    }
                }
                val out = ByteArrayOutputStream()
                if (!candidate.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    return FitOutcome.Unreadable
                }
                if (out.size().toLong() <= request.budgetBytes) {
                    // Downscaling may have turned a PNG or WebP into a JPEG, so the
                    // part has to be typed from the bytes actually being sent.
                    return FitOutcome.Fitted(
                        bytes = out.toByteArray(),
                        mimeType = JPEG,
                        width = candidate.width,
                        height = candidate.height,
                    )
                }
            }
            FitOutcome.TooLarge
        } finally {
            current?.recycle()
            decoded.recycle()
        }
    }

    companion object {
        const val JPEG = "image/jpeg"

        /** Descending ladder: the first entry that fits is used. */
        val DEFAULT_QUALITIES = intArrayOf(85, 75, 60, 45)

        /** Shorter ladder tried at every size step but the last. */
        val FIRST_QUALITIES = intArrayOf(85, 75)

        /** Fractions of the start size, largest first. */
        val SIZE_STEPS = doubleArrayOf(1.0, 0.85, 0.7, 0.55, 0.4, 0.3)

        /**
         * Long edge used when the carrier declared no image bounds.
         *
         * Enough to keep a phone photo sharp on any screen, small enough that a
         * modern photo does not spend the whole byte budget on pixels.
         */
        const val MAX_UNREPORTED_EDGE = 1600

        /**
         * The (width, height, quality) candidates to try, sharpest first.
         *
         * Quality is spent before resolution: every size step is tried at high
         * quality before the next, smaller size is considered, so a generous
         * byte budget keeps the pixels and only a tight one costs them. Pure, so
         * the order is pinned by unit tests rather than by a device.
         */
        fun searchPlan(
            startWidth: Int,
            startHeight: Int,
            sizeSteps: DoubleArray = SIZE_STEPS,
            firstQualities: IntArray = FIRST_QUALITIES,
            lastQualities: IntArray = DEFAULT_QUALITIES,
        ): List<Triple<Int, Int, Int>> {
            val plan = mutableListOf<Triple<Int, Int, Int>>()
            sizeSteps.forEachIndexed { index, step ->
                val width = max(1, (startWidth * step).toInt())
                val height = max(1, (startHeight * step).toInt())
                val ladder = if (index == sizeSteps.lastIndex) lastQualities else firstQualities
                ladder.forEach { quality -> plan.add(Triple(width, height, quality)) }
            }
            return plan
        }
    }
}
