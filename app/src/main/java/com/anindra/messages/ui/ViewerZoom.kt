package com.anindra.messages.ui

/**
 * Zoom and pan rules for the full-screen picture viewer (#460).
 *
 * The viewer transforms a graphics layer: unlike the chat's pinch zoom (#348),
 * which scales text and lets the bubbles re-lay out, a picture has one layout
 * and only its pixels change size. Kept as plain floats so the rules can be
 * tested without a Compose runtime.
 */
internal object ViewerZoom {

    /** Fit-to-screen. Zooming out past this would leave the picture floating. */
    const val MIN = 1f

    const val MAX = 5f

    /** Where a double tap lands from fit, and where it returns to. */
    const val DOUBLE_TAP = 2.5f

    /** The scale after a pinch of [zoom], bounded by [MIN]..[MAX]. */
    fun apply(current: Float, zoom: Float): Float = (current * zoom).coerceIn(MIN, MAX)

    /** The scale a double tap switches to: fit when already zoomed, else [DOUBLE_TAP]. */
    fun doubleTapTarget(current: Float): Float = if (current > MIN + 0.01f) MIN else DOUBLE_TAP

    /**
     * How far the picture may be dragged along one axis before its own edge
     * would leave the screen. Zero while it fits, so a fitted picture cannot be
     * nudged off centre.
     */
    fun maxPan(scale: Float, extent: Float): Float =
        ((scale - 1f) * extent / 2f).coerceAtLeast(0f)

    fun clampPan(pan: Float, scale: Float, extent: Float): Float {
        val limit = maxPan(scale, extent)
        return pan.coerceIn(-limit, limit)
    }

    /**
     * The translation that keeps the content under a pinch's fingers still.
     *
     * [centroid] and [offset] are both measured from the view's centre. Holding
     * the point under the fingers is what makes a pinch feel attached to the
     * picture instead of pulling everything towards the middle.
     */
    fun anchoredPan(
        pan: Float,
        offset: Float,
        centroid: Float,
        scale: Float,
        next: Float
    ): Float {
        val ratio = next / scale
        return pan + ratio * offset + (1f - ratio) * centroid
    }
}
