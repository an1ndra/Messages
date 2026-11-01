package com.anindra.messages.ui

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Rect
import com.anindra.messages.R

/**
 * What a drag away from the record button means.
 *
 * Google Messages gives the gesture two exits: slide to drop the note, or swipe
 * up to keep recording hands-free. Both are decided from the same running drag,
 * so the state has to be one value rather than two booleans that can disagree.
 */
internal enum class VoiceArm { NONE, CANCEL, LOCK }

internal object VoiceGesture {

    /** How far a finger travels before the cancel arms. */
    const val THRESHOLD_DP = 72

    /** How long the button has to be held before it counts as a recording. */
    const val HOLD_DELAY_MS = 260L

    /**
     * How long the finger has to rest on the lock target before the note locks.
     *
     * Reaching the padlock is the intent; a swipe that merely passes over it on
     * its way somewhere else is not. The dwell is what separates the two, and
     * it is short enough that deliberately stopping on the padlock feels like
     * one gesture.
     */
    const val LOCK_DWELL_MS = 300L

    /**
     * [dx] and [dy] are the drag so far in pixels, with the screen's axes: left
     * and up are negative. [overLock] is whether the finger itself is on the
     * padlock drawn above the button.
     *
     * A diagonal counts as a cancel, not a lock: throwing the note away has to
     * be the easier of the two to reach by accident. The lock is a place the
     * finger has to travel to, not a distance it has to cover, so a hand that
     * drifts up mid-sentence arms nothing (#364).
     */
    fun armFor(dx: Float, dy: Float, threshold: Float, overLock: Boolean): VoiceArm = when {
        dx <= -threshold -> VoiceArm.CANCEL
        overLock -> VoiceArm.LOCK
        else -> VoiceArm.NONE
    }
}

/**
 * The padlock stalk above the record button, and the part of it a finger has to
 * reach to lock a note.
 *
 * Both the drawing and the hit area read these numbers. The hit area is placed
 * from the stalk's own measured bounds rather than from the button's, because a
 * target derived from the button drifts off the icon as soon as the stalk's
 * layout shifts — and a lock that arms below the padlock is the sensitivity the
 * reporter kept hitting (#364).
 */
internal object VoiceLockTab {
    /** The record button this stalk hangs from. */
    const val BUTTON_DP = 56f
    const val WIDTH_DP = 52f
    /** How much of the tab shows above the button. 1.5x the original 76dp. */
    const val VISIBLE_DP = 114f
    /**
     * How far the tab continues below the button's top edge, so its bottom is
     * hidden behind the disc. Without this the tab ends just above the disc and
     * reads as a white slab floating over the chat (measured: 24px of seam).
     */
    const val TUCK_DP = 28f
    const val HEIGHT_DP = VISIBLE_DP + TUCK_DP
    /**
     * The tab is a child of the 56dp button box, and a Box centres a child
     * larger than itself, so placing the tab's top [VISIBLE_DP] above the
     * button means compensating for that centring: measured, a plain
     * `-VISIBLE_DP` put the tab 43dp too high and its bottom above the disc.
     */
    const val OFFSET_DP = -VISIBLE_DP - (BUTTON_DP - HEIGHT_DP) / 2f
    const val RADIUS_DP = 26f
    const val ICON_TOP_DP = 18f
    const val ICON_SIZE_DP = 22f
    const val ICON_GAP_DP = 14f
    const val CHEVRON_SIZE_DP = 22f

    /** How far around the icon still counts as being on it. */
    const val HIT_SLACK_DP = 6f

    /**
     * True when a finger at ([x], [y]) — pixels in the root, as the pointer
     * reports them plus the button's own origin — is on the padlock, given the
     * stalk's measured bounds.
     *
     * An unmeasured stalk ([tabWidth] 0) is not a lock: without the rectangle
     * there is nothing to aim at.
     */
    /** The padlock's hit rectangle, in root pixels, for a stalk measured at
     *  ([tabLeft], [tabTop]) with [tabWidth]. The log line Diagnostics-style
     *  scripts read comes from here, so the reported target cannot disagree with
     *  the one the gesture uses. */
    fun iconBounds(tabLeft: Float, tabTop: Float, tabWidth: Float, density: Float): Rect {
        val centreX = tabLeft + tabWidth / 2f
        val half = (ICON_SIZE_DP / 2f + HIT_SLACK_DP) * density
        return Rect(
            left = centreX - half,
            top = tabTop + (ICON_TOP_DP - HIT_SLACK_DP) * density,
            right = centreX + half,
            bottom = tabTop + (ICON_TOP_DP + ICON_SIZE_DP + HIT_SLACK_DP) * density
        )
    }

    fun reached(
        x: Float,
        y: Float,
        tabLeft: Float,
        tabTop: Float,
        tabWidth: Float,
        density: Float
    ): Boolean {
        if (tabWidth <= 0f) return false
        val bounds = iconBounds(tabLeft, tabTop, tabWidth, density)
        return x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom
    }
}

/** How the recording bar is acted on right now. */
internal enum class VoiceBarMode {
    /** A finger is still down: sliding left drops the note. */
    HOLD,
    /** The slide has gone far enough; lifting now drops the note. */
    CANCEL,
    /** The finger is gone and the note keeps running until send or discard. */
    LOCKED
}

/**
 * The recording bar's own state, decided once.
 *
 * While a note is locked there is no finger on the screen, so a bar still
 * saying "Slide to cancel" is describing a gesture nobody can make (#364). The
 * hint, the chevron and the bin's behaviour all follow one value instead of
 * being worked out separately at the call site.
 */
internal object VoiceBar {

    /** How far the bar's contents follow the finger. */
    const val SLIDE_FOLLOW = 0.4f

    fun modeFor(locked: Boolean, arm: VoiceArm): VoiceBarMode = when {
        locked -> VoiceBarMode.LOCKED
        arm == VoiceArm.CANCEL -> VoiceBarMode.CANCEL
        else -> VoiceBarMode.HOLD
    }

    @StringRes
    fun hintRes(mode: VoiceBarMode): Int = when (mode) {
        VoiceBarMode.HOLD -> R.string.chat_voice_slide_cancel
        VoiceBarMode.CANCEL -> R.string.chat_voice_release_cancel
        VoiceBarMode.LOCKED -> R.string.chat_voice_recording_locked
    }

    /** The chevron points at the slide target, so it has no meaning once the
     *  recording is locked; the lock icon takes its place instead. */
    fun showsChevron(mode: VoiceBarMode): Boolean = mode != VoiceBarMode.LOCKED

    fun showsLock(mode: VoiceBarMode): Boolean = mode == VoiceBarMode.LOCKED

    /** Mid-hold the bin is the target the slide aims at, not a button. Locked,
     *  it is the only way to discard the note, so it has to be pressable. */
    fun binIsButton(mode: VoiceBarMode): Boolean = mode == VoiceBarMode.LOCKED

    /** The bar travels left with the drag, so the slide visibly does something.
     *  Clamped so the row cannot wander out of the pill. */
    fun slideOffset(dxPx: Float, capPx: Float): Float = (dxPx * SLIDE_FOLLOW).coerceIn(-capPx, 0f)
}
