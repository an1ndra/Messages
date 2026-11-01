package com.anindra.messages.ui

/**
 * What a drag away from the record button means.
 *
 * Google Messages gives the gesture two exits: slide to drop the note, or swipe
 * up to keep recording hands-free. Both are decided from the same running drag,
 * so the state has to be one value rather than two booleans that can disagree.
 */
internal enum class VoiceArm { NONE, CANCEL, LOCK }

internal object VoiceGesture {

    /** How far a finger travels before either exit arms. */
    const val THRESHOLD_DP = 72

    /**
     * [dx] and [dy] are the drag so far in pixels, with the screen's axes: left
     * and up are negative.
     *
     * A diagonal to the upper left counts as a cancel, not a lock: throwing the
     * note away has to be the easier of the two to reach by accident.
     */
    fun armFor(dx: Float, dy: Float, threshold: Float): VoiceArm = when {
        dx <= -threshold -> VoiceArm.CANCEL
        dy <= -threshold -> VoiceArm.LOCK
        else -> VoiceArm.NONE
    }
}
