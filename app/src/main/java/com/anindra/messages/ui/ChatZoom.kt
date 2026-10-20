package com.anindra.messages.ui

/**
 * Pinch-to-zoom on the conversation view (#348).
 *
 * Zoom drives the chat's font scale, not a graphics-layer scale: text grows and
 * the bubbles re-lay out around it, so nothing is cropped and no blank space
 * appears. Bounds are fixed so a stray pinch cannot shrink a chat to nothing or
 * blow a message up past the bubble width.
 */
object ChatZoom {
    const val DEFAULT = 1f
    const val MIN = 0.8f
    const val MAX = 2f

    /** The next zoom for a pinch of [zoomChange], clamped to [MIN]..[MAX]. */
    fun apply(current: Float, zoomChange: Float): Float =
        (current * zoomChange).coerceIn(MIN, MAX)

    /** The font-scale multiplier the message list renders at. */
    fun fontScale(zoom: Float): Float = zoom.coerceIn(MIN, MAX)

    fun isDefault(zoom: Float): Boolean = kotlin.math.abs(zoom - DEFAULT) < 0.01f
}
