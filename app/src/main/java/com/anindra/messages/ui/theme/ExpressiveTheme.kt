package com.anindra.messages.ui.theme

import androidx.compose.material3.MotionScheme

/**
 * M3 Expressive theming. Expressive motion is spring-based; accessibility's
 * reduce-motion option falls back to the standard (non-spring) scheme so
 * components stop animating.
 */
object ExpressiveTheme {
    fun motionScheme(reduceMotion: Boolean): MotionScheme =
        if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive()
}
