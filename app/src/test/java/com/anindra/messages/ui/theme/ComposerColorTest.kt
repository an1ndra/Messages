package com.anindra.messages.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerColorTest {

    @Test
    fun theLightComposerStripIsTheChatPagesOwnColour() {
        // A grey band behind the field inside a white chat read as a container
        // the field was trapped in.
        assertEquals(LightColors.background, LightColors.chatBar)
        assertEquals(LightColors.surfaceContainerHigh, LightColors.inputPill)
    }

    @Test
    fun theFieldStaysDistinctFromTheStripItSitsOn() {
        assertTrue(
            "the field must not disappear into the bar",
            LightColors.inputPill != LightColors.chatBar
        )
    }

    @Test
    fun darkModeKeepsTheRaisedContainers() {
        // In dark the page is the darker surface, so both the bar and the field
        // climb the container ladder instead of matching the page.
        assertEquals(DarkColors.surfaceContainerLow, DarkColors.chatBar)
        assertEquals(DarkColors.surfaceContainerHigh, DarkColors.inputPill)
    }
}
