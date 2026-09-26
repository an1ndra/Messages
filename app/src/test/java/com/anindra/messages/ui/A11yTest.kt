package com.anindra.messages.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class A11yTest {

    @Test
    fun touchTargetNeverShrinksBelowTheMinimum() {
        assertEquals(A11y.MIN_TOUCH_DP.dp, A11y.touchTarget(32.dp))
        assertEquals(A11y.MIN_TOUCH_DP.dp, A11y.touchTarget(47.9f.dp))
        assertEquals(A11y.MIN_TOUCH_DP.dp, A11y.touchTarget(48.dp))
    }

    @Test
    fun touchTargetKeepsLargerSizes() {
        assertEquals(56.dp, A11y.touchTarget(56.dp))
    }

    @Test
    fun describeJoinsNonBlankPartsWithAPeriod() {
        assertEquals("Ann. Hello. 10:00", A11y.describe("Ann", "Hello", "10:00"))
    }

    @Test
    fun describeDropsNullAndBlankParts() {
        assertEquals("Ann. Hello", A11y.describe("Ann", "  ", null, "", "Hello"))
        assertEquals("", A11y.describe(null, " ", ""))
    }
}
