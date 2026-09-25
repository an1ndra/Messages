package com.anindra.messages.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun usesMaterial3DurationTokens() {
        assertEquals(200, Motion.DURATION_SHORT4)
        assertEquals(250, Motion.DURATION_MEDIUM1)
        assertEquals(300, Motion.DURATION_MEDIUM2)
        assertEquals(450, Motion.DURATION_LONG1)
    }

    @Test
    fun emphasizedCurveMatchesMaterial3() {
        assertEquals(0.2f, Motion.EMPHASIZED_POINTS[0], 0.0001f)
        assertEquals(0f, Motion.EMPHASIZED_POINTS[1], 0.0001f)
        assertEquals(0f, Motion.EMPHASIZED_POINTS[2], 0.0001f)
        assertEquals(1f, Motion.EMPHASIZED_POINTS[3], 0.0001f)
    }

    @Test
    fun emphasizedVariantsMatchMaterial3() {
        assertEquals(0.05f, Motion.EMPHASIZED_DECELERATE_POINTS[0], 0.0001f)
        assertEquals(0.7f, Motion.EMPHASIZED_DECELERATE_POINTS[1], 0.0001f)
        assertEquals(0.1f, Motion.EMPHASIZED_DECELERATE_POINTS[2], 0.0001f)
        assertEquals(1f, Motion.EMPHASIZED_DECELERATE_POINTS[3], 0.0001f)
        assertEquals(0.3f, Motion.EMPHASIZED_ACCELERATE_POINTS[0], 0.0001f)
        assertEquals(0f, Motion.EMPHASIZED_ACCELERATE_POINTS[1], 0.0001f)
        assertEquals(0.8f, Motion.EMPHASIZED_ACCELERATE_POINTS[2], 0.0001f)
        assertEquals(0.15f, Motion.EMPHASIZED_ACCELERATE_POINTS[3], 0.0001f)
    }

    @Test
    fun reduceMotionCollapsesDurationToZero() {
        assertEquals(300, Motion.durationMs(false, Motion.DURATION_MEDIUM2))
        assertEquals(0, Motion.durationMs(true, Motion.DURATION_MEDIUM2))
        assertEquals(0, Motion.durationMs(true, Motion.DURATION_SHORT4))
    }

    @Test
    fun reduceMotionReportsNoAnimation() {
        assertTrue(Motion.animates(false))
        assertFalse(Motion.animates(true))
    }

    @Test
    fun reduceMotionEasingIsLinearSoNothingOvershoots() {
        val linear = Motion.easing(true, Motion.EMPHASIZED_POINTS)
        assertEquals(linear.transform(0.25f), 0.25f, 0.0001f)
        assertEquals(linear.transform(0.5f), 0.5f, 0.0001f)
        assertEquals(linear.transform(0.75f), 0.75f, 0.0001f)
    }

    @Test
    fun emphasizedEasingIsNotLinear() {
        val emphasized = Motion.emphasized(false)
        assertTrue(emphasized.transform(0.5f) > 0.5f)
    }

    @Test
    fun emphasizedEasingEndpointsArePinned() {
        val emphasized = Motion.emphasized(false)
        assertEquals(0f, emphasized.transform(0f), 0.0001f)
        assertEquals(1f, emphasized.transform(1f), 0.0001f)
    }

    @Test
    fun specsCollapseToSnapUnderReduceMotion() {
        val reduced: FiniteAnimationSpec<Float> = motionTween(reduceMotion = true)
        val normal: FiniteAnimationSpec<Float> =
            motionTween(reduceMotion = false, durationMs = Motion.DURATION_MEDIUM2)
        assertTrue(reduced is androidx.compose.animation.core.SnapSpec)
        assertEquals(
            Motion.DURATION_MEDIUM2,
            (normal as androidx.compose.animation.core.TweenSpec<Float>).durationMillis
        )
    }

    @Test
    fun springSpecCollapsesToSnapUnderReduceMotion() {
        val reduced: FiniteAnimationSpec<Float> = motionSpring(reduceMotion = true)
        assertTrue(reduced is androidx.compose.animation.core.SnapSpec)
        val normal: FiniteAnimationSpec<Float> = motionSpring(reduceMotion = false)
        assertTrue(normal is androidx.compose.animation.core.SpringSpec)
    }
}
