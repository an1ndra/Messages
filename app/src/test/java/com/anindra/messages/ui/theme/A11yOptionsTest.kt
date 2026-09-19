package com.anindra.messages.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A11yOptionsTest {

    @Test
    fun disabledMasterKeepsEveryOptionInactive() {
        val options = A11yOptions(
            enabled = false,
            fontScalePercent = 130,
            bold = true,
            highContrast = true,
            reduceMotion = true,
            largeTouchTargets = true
        )
        assertEquals(1f, options.fontScale, 0.0001f)
        assertFalse(options.boldEnabled)
        assertFalse(options.highContrastEnabled)
        assertFalse(options.reduceMotionEnabled)
        assertFalse(options.largeTouchTargetsEnabled)
    }

    @Test
    fun enabledMasterAppliesSelectedOptions() {
        val options = A11yOptions(
            enabled = true,
            fontScalePercent = 130,
            bold = true,
            highContrast = true,
            reduceMotion = true,
            largeTouchTargets = true
        )
        assertEquals(1.30f, options.fontScale, 0.0001f)
        assertTrue(options.boldEnabled)
        assertTrue(options.highContrastEnabled)
        assertTrue(options.reduceMotionEnabled)
        assertTrue(options.largeTouchTargetsEnabled)
    }

    @Test
    fun defaultFontScaleIsNeutralWhenEnabled() {
        assertEquals(1f, A11yOptions(enabled = true).fontScale, 0.0001f)
    }

    @Test
    fun fontScaleClampsOutOfRangeValues() {
        assertEquals(0.85f, A11yOptions(enabled = true, fontScalePercent = 10).fontScale, 0.0001f)
        assertEquals(1.30f, A11yOptions(enabled = true, fontScalePercent = 500).fontScale, 0.0001f)
    }
}
