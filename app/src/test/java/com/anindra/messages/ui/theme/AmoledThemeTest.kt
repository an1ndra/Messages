package com.anindra.messages.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AMOLED scheme's contract.
 *
 * Two things matter and are easy to regress. First, the page must actually be
 * black: an off-black background means lit pixels on an OLED panel, which is
 * the entire point of the theme. Second, the raised containers must still be
 * distinguishable from that black, because cards, the composer and the
 * incoming bubble all sit on them -- if they were left at #000000 the layout
 * would render but be unreadable.
 */
class AmoledThemeTest {

    private val amoled: ColorScheme = AmoledColors

    @Test
    fun pageColoursArePureBlack() {
        assertEquals(Color(0xFF000000), amoled.background)
        assertEquals(Color(0xFF000000), amoled.surface)
        assertEquals(Color(0xFF000000), amoled.surfaceContainerLowest)
        assertEquals(Color(0xFF000000), amoled.surfaceDim)
    }

    @Test
    fun everyRaisedContainerIsVisibleAgainstBlack() {
        val raised = mapOf(
            "surfaceBright" to amoled.surfaceBright,
            "surfaceContainerHigh" to amoled.surfaceContainerHigh,
            "surfaceContainerHighest" to amoled.surfaceContainerHighest,
            "surfaceContainer" to amoled.surfaceContainer,
            "surfaceContainerLow" to amoled.surfaceContainerLow
        )
        for ((role, color) in raised) {
            assertNotEquals("$role must not be black, or cards vanish", Color(0xFF000000), color)
        }
    }

    @Test
    fun containersStepMonotonicallyOffBlack() {
        // Cards are told apart by how far they sit from the page, so the ramp
        // has to be ordered. Unsorted steps make two different roles render the
        // same shade, which is invisible in the colour values alone.
        val ramp = listOf(
            amoled.surfaceContainerLow,
            amoled.surfaceContainer,
            amoled.surfaceContainerHigh,
            amoled.surfaceContainerHighest
        )
        for (i in 0 until ramp.size - 1) {
            assertTrue(
                "step $i (${ramp[i]}) is not lighter than step ${i + 1} (${ramp[i + 1]})",
                ramp[i].luminance() < ramp[i + 1].luminance()
            )
        }
    }

    @Test
    fun textOnThePageMeetsContrastOnBlack() {
        // WCAG AA for body text. onSurface is what every label uses, so it is
        // the one that has to hold up.
        val ratio = contrast(amoled.onSurface, amoled.background)
        assertTrue("onSurface on background is $ratio:1, needs 4.5:1", ratio >= 4.5f)
    }

    @Test
    fun bubblesStayDistinguishableFromEachOther() {
        // The outgoing bubble is primaryContainer and the incoming one is
        // surfaceContainerHighest. Under AMOLED both sit on black, so they must
        // not collapse into the same colour.
        assertNotEquals(amoled.outgoingBubble, amoled.incomingBubble)
        assertNotEquals(amoled.incomingBubble, amoled.background)
    }

    @Test
    fun theComposerBarIsSeparableFromThePage() {
        // chatBar is what sits behind the input field. On a dark scheme it is
        // surfaceContainerLow, which has to differ from the black page or the
        // field loses its edge.
        assertNotEquals(amoled.chatBar, amoled.background)
    }

    @Test
    fun amoledIsNotTheSameAsTheDarkScheme() {
        assertNotEquals(DarkColors.background, amoled.background)
    }

    @Test
    fun amoledStaysBlackInLightModeContext() {
        // The semantic aliases branch on background luminance rather than on the
        // mode string, so a true-black page has to land on the dark branch or
        // the selected-bubble colours would be picked as if the page were light.
        assertTrue(amoled.background.luminance() < 0.5f)
        assertEquals(Color(0xFF9CC0FF), amoled.selectedBubble)
        assertEquals(Color(0xFF062E6F), amoled.onSelectedBubble)
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }
}
