package com.anindra.messages.mms.fit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Scales an attachment down to the dimensions the carrier advertises.
 *
 * Carriers reject an image wider or taller than their configured maximum, and
 * doing nothing produces a send failure the user cannot act on. A non-positive
 * bound means "unbounded", matching a carrier that omits the key.
 */
object ImageSizing {
    data class Size(val width: Int, val height: Int)

    /**
     * @return the resized dimensions, or null when the image already fits, cannot
     *   be measured, or both bounds are unbounded.
     */
    fun fitWithin(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Size? {
        if (width <= 0 || height <= 0) return null
        val boundedWidth = maxWidth > 0
        val boundedHeight = maxHeight > 0
        if (!boundedWidth && !boundedHeight) return null

        var scale = 1.0
        if (boundedWidth) scale = min(scale, maxWidth.toDouble() / width)
        if (boundedHeight) scale = min(scale, maxHeight.toDouble() / height)
        if (scale >= 1.0) return null

        // Rounding an extreme aspect ratio can reach 0 on one axis, which yields an
        // undecodable bitmap, so clamp to at least one pixel.
        return Size(
            max(1, (width * scale).roundToInt()),
            max(1, (height * scale).roundToInt()),
        )
    }

    /**
     * Power-of-two subsampling that still leaves the decode at or above [target],
     * so the final resize is a cheap scale rather than a full-size realloc. Purely
     * a decode optimisation — the caller scales to [target] either way.
     */
    fun sampleSize(width: Int, height: Int, target: Size?): Int {
        if (target == null || width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= target.width && height / (sample * 2) >= target.height) {
            sample *= 2
        }
        return sample
    }
}
