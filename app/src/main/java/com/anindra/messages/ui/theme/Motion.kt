package com.anindra.messages.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Material 3 motion tokens. */
object Motion {
    const val DURATION_SHORT4 = 200
    const val DURATION_MEDIUM1 = 250
    const val DURATION_MEDIUM2 = 300
    const val DURATION_LONG1 = 450
    const val SHIMMER_DURATION_MS = 1100

    val EMPHASIZED_POINTS = floatArrayOf(0.2f, 0f, 0f, 1f)
    val EMPHASIZED_DECELERATE_POINTS = floatArrayOf(0.05f, 0.7f, 0.1f, 1f)
    val EMPHASIZED_ACCELERATE_POINTS = floatArrayOf(0.3f, 0f, 0.8f, 0.15f)
    val STANDARD_POINTS = floatArrayOf(0.2f, 0f, 0f, 1f)

    const val SPATIAL_STIFFNESS_MEDIUM = Spring.StiffnessMediumLow
    const val SPATIAL_DAMPING_NO_BOUNCY = Spring.DampingRatioNoBouncy
    const val EFFECTIVE_STIFFNESS_MEDIUM = Spring.StiffnessMediumLow

    fun durationMs(reduceMotion: Boolean, token: Int): Int =
        if (reduceMotion) 0 else token

    fun easing(reduceMotion: Boolean, points: FloatArray): Easing =
        if (reduceMotion) LinearEasing else CubicBezierEasing(
            points[0], points[1], points[2], points[3]
        )

    fun animates(reduceMotion: Boolean): Boolean = !reduceMotion

    fun emphasized(reduceMotion: Boolean = false): Easing =
        easing(reduceMotion, EMPHASIZED_POINTS)

    fun emphasizedDecelerate(reduceMotion: Boolean = false): Easing =
        easing(reduceMotion, EMPHASIZED_DECELERATE_POINTS)

    fun emphasizedAccelerate(reduceMotion: Boolean = false): Easing =
        easing(reduceMotion, EMPHASIZED_ACCELERATE_POINTS)
}

fun motionDurationMs(reduceMotion: Boolean, token: Int): Int =
    Motion.durationMs(reduceMotion, token)

/** These collapse to an instant [snap] under reduce-motion, so callers never
 *  have to branch on the accessibility option themselves. */
fun <T> motionTween(
    reduceMotion: Boolean,
    durationMs: Int = Motion.DURATION_MEDIUM2,
    easing: Easing = Motion.emphasized(reduceMotion)
): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(durationMs, easing = easing)

fun <T> motionSpring(
    reduceMotion: Boolean,
    dampingRatio: Float = Motion.SPATIAL_DAMPING_NO_BOUNCY,
    stiffness: Float = Motion.SPATIAL_STIFFNESS_MEDIUM
): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else spring(dampingRatio = dampingRatio, stiffness = stiffness)
