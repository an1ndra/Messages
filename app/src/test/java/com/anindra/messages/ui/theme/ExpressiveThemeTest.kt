package com.anindra.messages.ui.theme

import androidx.compose.material3.MotionScheme
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ExpressiveThemeTest {

    @Test
    fun motionIsExpressiveByDefault() {
        assertSame(MotionScheme.expressive(), ExpressiveTheme.motionScheme(reduceMotion = false))
    }

    @Test
    fun reduceMotionFallsBackToStandardMotion() {
        assertSame(MotionScheme.standard(), ExpressiveTheme.motionScheme(reduceMotion = true))
    }

    @Test
    fun reduceMotionActuallySwitchesTheScheme() {
        assertNotSame(
            ExpressiveTheme.motionScheme(reduceMotion = false),
            ExpressiveTheme.motionScheme(reduceMotion = true)
        )
    }
}

