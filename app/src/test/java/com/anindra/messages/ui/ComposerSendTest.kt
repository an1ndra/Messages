package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The send circle is always on screen; it is only live when there is a message
 * or a staged picture, so an empty composer cannot fire a send.
 */
class ComposerSendTest {

    @Test
    fun anEmptyComposerHasNothingToSend() {
        assertFalse(canSend("", hasAttachments = false))
    }

    @Test
    fun whitespaceAloneIsNotAMessage() {
        assertFalse(canSend("   ", hasAttachments = false))
        assertFalse(canSend("\n\t ", hasAttachments = false))
    }

    @Test
    fun typedTextEnablesSend() {
        assertTrue(canSend("hello", hasAttachments = false))
        assertTrue(canSend(" hello ", hasAttachments = false))
    }

    @Test
    fun aStagedPictureEnablesSendWithoutText() {
        assertTrue(canSend("", hasAttachments = true))
        assertTrue(canSend("   ", hasAttachments = true))
    }

    @Test
    fun textAndAPictureBothEnableSend() {
        assertTrue(canSend("caption", hasAttachments = true))
    }

    @Test
    fun clearingTheDraftTurnsSendOffAgain() {
        assertTrue(canSend("draft", hasAttachments = false))
        assertFalse(canSend("", hasAttachments = false))
    }
}
