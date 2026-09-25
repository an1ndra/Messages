package com.anindra.messages.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Material 3 motion tokens. Every custom animation in the app should take its
 * duration, easing and spring from here instead of inventing inline values, so
 * that motion stays consistent and can be centrally disabled.
 *
 * The easing curves and duration values are the M3 tokens; the pure accessors
 * live on [Motion] so they can be unit tested without a composition.
 */
object Motion {
    /** M3 duration tokens, in milliseconds. */
    const val DURATION_SHORT4 = 200
    const val DURATION_MEDIUM1 = 250
    const val DURATION_MEDIUM2 = 300
    const val DURATION_LONG1 = 450

    /** Indefinite loading shimmer cycle; not a finite transition token. */
    const val SHIMMER_DURATION_MS = 1100

    /** Control points of the M3 emphasized easing curve. */
    val EMPHASIZED_POINTS = floatArrayOf(0.2f, 0f, 0f, 1f)
    val EMPHASIZED_DECELERATE_POINTS = floatArrayOf(0.05f, 0.7f, 0.1f, 1f)
    val EMPHASIZED_ACCELERATE_POINTS = floatArrayOf(0.3f, 0f, 0.8f, 0.15f)
    val STANDARD_POINTS = floatArrayOf(0.2f, 0f, 0f, 1f)

    /** M3 spring tokens, kept as raw values so they stay testable. */
    const val SPATIAL_STIFFNESS_MEDIUM = Spring.StiffnessMediumLow
    const val SPATIAL_DAMPING_NO_BOUNCY = Spring.DampingRatioNoBouncy
    const val EFFECTIVE_STIFFNESS_MEDIUM = Spring.StiffnessMediumLow

    /**
     * Duration actually used for a transition. Reduce-motion collapses it to
     * zero so the change still happens, it just arrives instantly.
     */
    fun durationMs(reduceMotion: Boolean, token: Int): Int =
        if (reduceMotion) 0 else token

    /** Easing used while reduce-motion is on: identity, so nothing overshoots. */
    fun easing(reduceMotion: Boolean, points: FloatArray): Easing =
        if (reduceMotion) LinearEasing else CubicBezierEasing(
            points[0], points[1], points[2], points[3]
        )

    /** True when a transition may run at all. */
    fun animates(reduceMotion: Boolean): Boolean = !reduceMotion

    fun emphasized(reduceMotion: Boolean = false): Easing =
        easing(reduceMotion, EMPHASIZED_POINTS)

    fun emphasizedDecelerate(reduceMotion: Boolean = false): Easing =
        easing(reduceMotion, EMPHASIZED_DECELERATE_POINTS)

    fun emphasizedAccelerate(reduceMotion: Boolean = false): Easing =
        easing(reduceMotion, EMPHASIZED_ACCELERATE_POINTS)
}

/** Duration token to hand to [motionTween], honouring reduce-motion. */
fun motionDurationMs(reduceMotion: Boolean, token: Int): Int =
    Motion.durationMs(reduceMotion, token)

/**
 * A tween on the M3 emphasized curve, or an instant [snap] when reduce-motion is
 * enabled, so callers never have to branch on the accessibility option.
 */
fun <T> motionTween(
    reduceMotion: Boolean,
    durationMs: Int = Motion.DURATION_MEDIUM2,
    easing: Easing = Motion.emphasized(reduceMotion)
): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(durationMs, easing = easing)

/** M3 spatial spring for shape and position, or an instant [snap] on reduce-motion. */
fun <T> motionSpring(
    reduceMotion: Boolean,
    dampingRatio: Float = Motion.SPATIAL_DAMPING_NO_BOUNCY,
    stiffness: Float = Motion.SPATIAL_STIFFNESS_MEDIUM
): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else spring(dampingRatio = dampingRatio, stiffness = stiffness)
