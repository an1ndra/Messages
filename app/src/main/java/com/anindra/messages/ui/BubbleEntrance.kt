package com.anindra.messages.ui

/** Pure math + gating for the chat bubble entrance animation, kept out of the
 *  composable so it can be unit tested. */
object BubbleEntrance {
    const val DURATION_MS = 260
    const val INITIAL_SCALE = 0.86f
    const val SLIDE_DP = 22f

    /** A bubble animates only when it arrives after the screen's baseline and
     *  has not already played its entrance (LazyColumn disposes off-screen rows
     *  and would otherwise replay on every scroll-back). Accessibility mode's
     *  reduce-motion option suppresses it entirely. */
    fun shouldAnimate(
        messageId: Long,
        baselineId: Long,
        alreadyAnimated: Boolean,
        reduceMotion: Boolean = false
    ): Boolean = !reduceMotion && baselineId > 0 && messageId > baselineId && !alreadyAnimated

    /** Outgoing bubbles slide in from the right, incoming from the left. */
    fun direction(isMe: Boolean): Float = if (isMe) 1f else -1f

    fun translationX(progress: Float, isMe: Boolean, slidePx: Float): Float =
        direction(isMe) * slidePx * (1f - progress)

    fun scale(progress: Float): Float =
        INITIAL_SCALE + (1f - INITIAL_SCALE) * progress.coerceIn(0f, 1f)

    fun alpha(progress: Float): Float = progress.coerceIn(0f, 1f)
}
