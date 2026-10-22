package com.anindra.messages.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pinch-to-zoom on the conversation view (#348).
 *
 * Zoom drives the chat's font scale, not a graphics-layer scale: text grows and
 * the bubbles re-lay out around it, so nothing is cropped and no blank space
 * appears. Bounds are fixed so a stray pinch cannot shrink a chat to nothing or
 * blow a message up past the bubble width.
 *
 * Everything here is pure so the anchoring maths — which is what makes a pinch
 * feel attached to the finger — can be tested without a device.
 */
object ChatZoom {
    const val DEFAULT = 1f
    const val MIN = 0.7f
    const val MAX = 2.5f

    /** Where a double-tap zooms to when the chat is at 100%. */
    const val DOUBLE_TAP = 1.5f

    /** The next zoom for a pinch of [zoomChange], clamped to [MIN]..[MAX]. */
    fun apply(current: Float, zoomChange: Float): Float =
        (current * zoomChange).coerceIn(MIN, MAX)

    /** The font-scale multiplier the message list renders at. */
    fun fontScale(zoom: Float): Float = zoom.coerceIn(MIN, MAX)

    fun isDefault(zoom: Float): Boolean = abs(zoom - DEFAULT) < 0.01f

    /** The zoom as the percentage the indicator shows ("150"). */
    fun percent(zoom: Float): Int = (fontScale(zoom) * 100f).roundToInt()

    /** One double-tap in, the next one back out. */
    fun doubleTapTarget(current: Float): Float =
        if (isDefault(current)) DOUBLE_TAP else DEFAULT

    /** A laid-out row as the anchoring maths needs it: [offset] and [size] are
     *  viewport pixels, [index] identifies the row across the re-layout. */
    data class Item(val index: Int, val offset: Int, val size: Int)

    /** What the finger is on: the row, and how far down that row the finger is
     *  (0 at its top edge, 1 at its bottom). */
    data class Anchor(val index: Int, val fraction: Float)

    /** The row under [y], or the nearest row when [y] lands in a gap between
     *  bubbles. Null only when nothing is laid out. */
    fun anchorAt(y: Float, items: List<Item>): Anchor? {
        if (items.isEmpty()) return null
        val hit = items.firstOrNull { y >= it.offset && y <= it.offset + it.size }
            ?: items.minByOrNull { abs(it.offset + it.size / 2f - y) }!!
        val fraction = ((y - hit.offset) / hit.size.coerceAtLeast(1)).coerceIn(0f, 1f)
        return Anchor(hit.index, fraction)
    }

    /**
     * The scroll that puts [anchor] back under [y] once [item] has re-laid out at
     * a new scale. Without it the list keeps its scroll offset while every row
     * changes height, so the message the user pinched slides out from under the
     * fingers — the "takes too much work" half of #348.
     *
     * Positive scrolls the content up, matching LazyListState.scrollBy.
     */
    fun anchorScroll(y: Float, anchor: Anchor, item: Item): Float =
        item.offset - (y - anchor.fraction * item.size)
}
