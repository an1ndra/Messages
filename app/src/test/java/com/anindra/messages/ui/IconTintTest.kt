package com.anindra.messages.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconTintTest {

    @Test
    fun darkModeKeepsMoreOfTheContentColor() {
        assertTrue(
            "a disabled icon must stay more visible on a dark surface",
            IconTint.disabledAlpha(isDark = true) > IconTint.disabledAlpha(isDark = false)
        )
    }

    @Test
    fun neitherStateIsFullyTransparentOrFullyOpaque() {
        listOf(true, false).forEach { dark ->
            val a = IconTint.disabledAlpha(dark)
            assertTrue("alpha $a must be > 0", a > 0f)
            assertTrue("alpha $a must be < 1", a < 1f)
        }
    }

    @Test
    fun disabledKeepsTheHueAndOnlyChangesAlpha() {
        val base = Color(0xFFC4C7C5)
        val out = IconTint.disabled(base, isDark = true)
        assertEquals(base.red, out.red, 0.001f)
        assertEquals(base.green, out.green, 0.001f)
        assertEquals(base.blue, out.blue, 0.001f)
        assertEquals(IconTint.disabledAlpha(true), out.alpha, 0.001f)
    }
}
