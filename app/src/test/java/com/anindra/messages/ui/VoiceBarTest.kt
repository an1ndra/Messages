package com.anindra.messages.ui

import com.anindra.messages.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recording bar's exits are described by one value (#364). A locked note has
 * no finger on the screen, so the bar must stop offering a slide nobody can
 * make, and must say the note is still running.
 */
class VoiceBarTest {

    @Test
    fun aHeldNoteOffersTheSlide() {
        val mode = VoiceBar.modeFor(locked = false, arm = VoiceArm.NONE)
        assertEquals(VoiceBarMode.HOLD, mode)
        assertEquals(R.string.chat_voice_slide_cancel, VoiceBar.hintRes(mode))
        assertTrue(VoiceBar.showsChevron(mode))
        assertFalse(VoiceBar.showsLock(mode))
        assertFalse("the bin is the slide target mid-hold, not a button", VoiceBar.binIsButton(mode))
    }

    @Test
    fun anArmedCancelAsksToRelease() {
        val mode = VoiceBar.modeFor(locked = false, arm = VoiceArm.CANCEL)
        assertEquals(VoiceBarMode.CANCEL, mode)
        assertEquals(R.string.chat_voice_release_cancel, VoiceBar.hintRes(mode))
        assertTrue(VoiceBar.showsChevron(mode))
    }

    @Test
    fun aLockedNoteSaysItIsRecordingHandsFree() {
        val mode = VoiceBar.modeFor(locked = true, arm = VoiceArm.NONE)
        assertEquals(VoiceBarMode.LOCKED, mode)
        assertEquals(R.string.chat_voice_recording_locked, VoiceBar.hintRes(mode))
        assertFalse("nothing to slide once the finger is gone", VoiceBar.showsChevron(mode))
        assertTrue(VoiceBar.showsLock(mode))
        assertTrue("the bin is the only way to drop a locked note", VoiceBar.binIsButton(mode))
    }

    @Test
    fun lockWinsOverALeftoverArm() {
        assertEquals(VoiceBarMode.LOCKED, VoiceBar.modeFor(locked = true, arm = VoiceArm.CANCEL))
        assertEquals(VoiceBarMode.LOCKED, VoiceBar.modeFor(locked = true, arm = VoiceArm.LOCK))
    }

    @Test
    fun theBarFollowsTheFingerLeftwardsOnly() {
        assertEquals(0f, VoiceBar.slideOffset(0f, capPx = 100f), 0.01f)
        assertTrue("a rightward drag must not carry the bar", VoiceBar.slideOffset(80f, capPx = 100f) <= 0f)
        assertEquals(-40f, VoiceBar.slideOffset(-100f, capPx = 100f), 0.01f)
        assertEquals("clamped so the row cannot leave the pill", -100f, VoiceBar.slideOffset(-900f, capPx = 100f), 0.01f)
    }
}
