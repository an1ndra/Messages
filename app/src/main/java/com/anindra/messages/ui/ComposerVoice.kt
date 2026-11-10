package com.anindra.messages.ui

import androidx.annotation.StringRes
import com.anindra.messages.R

/**
 * What a drag away from the record button means.
 *
 * Sliding left drops the note; there is no other exit. The arm is one value so
 * the bar and the release cannot disagree about it.
 */
internal enum class VoiceArm { NONE, CANCEL }

internal object VoiceGesture {

    /** How far a finger travels before the cancel arms. */
    const val THRESHOLD_DP = 72

    /** How long the button has to be held before it counts as a recording. */
    const val HOLD_DELAY_MS = 260L

    /**
     * [dx] is the drag so far in pixels, with the screen's axes: left is
     * negative. A diagonal still counts as a cancel: throwing the note away has
     * to be the easy exit (#364).
     */
    fun armFor(dx: Float, threshold: Float): VoiceArm =
        if (dx <= -threshold) VoiceArm.CANCEL else VoiceArm.NONE
}

/** How the recording bar is acted on right now. */
internal enum class VoiceBarMode {
    /** A finger is still down: sliding left drops the note. */
    HOLD,
    /** The slide has gone far enough; lifting now drops the note. */
    CANCEL
}

internal object VoiceBar {

    /** How far the bar's contents follow the finger. */
    const val SLIDE_FOLLOW = 0.4f

    fun modeFor(arm: VoiceArm): VoiceBarMode =
        if (arm == VoiceArm.CANCEL) VoiceBarMode.CANCEL else VoiceBarMode.HOLD

    @StringRes
    fun hintRes(mode: VoiceBarMode): Int = when (mode) {
        VoiceBarMode.HOLD -> R.string.chat_voice_slide_cancel
        VoiceBarMode.CANCEL -> R.string.chat_voice_release_cancel
    }

    /** The bar travels left with the drag, so the slide visibly does something.
     *  Clamped so the row cannot wander out of the pill. */
    fun slideOffset(dxPx: Float, capPx: Float): Float = (dxPx * SLIDE_FOLLOW).coerceIn(-capPx, 0f)

    /** How much the cancel bubble has been pulled, 0 at rest and 1 at the arm. */
    fun bubbleProgress(dxPx: Float, thresholdPx: Float): Float =
        if (thresholdPx <= 0f) 1f else (-dxPx / thresholdPx).coerceIn(0f, 1f)

    /** The bubble stretches the way it is pulled, and gives a little vertically,
     *  so the slide reads as a soft thing being dragged rather than a label
     *  sliding under a finger. */
    fun bubbleScaleX(progress: Float): Float =
        1f + BUBBLE_STRETCH * progress.coerceIn(0f, 1f)

    fun bubbleScaleY(progress: Float): Float =
        1f - BUBBLE_STRETCH * BUBBLE_SQUASH * progress.coerceIn(0f, 1f)

    /** How much of the drag reaches the bubble's width, at full arm. */
    const val BUBBLE_STRETCH = 0.10f

    /** The vertical give, as a fraction of the horizontal stretch. */
    const val BUBBLE_SQUASH = 0.6f

    /** The scale the bubble pops in from when the recording starts. */
    const val BUBBLE_POP_FROM = 0.72f
}
