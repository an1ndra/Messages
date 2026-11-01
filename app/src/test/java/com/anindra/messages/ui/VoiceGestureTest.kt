package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The record gesture has three exits and one running drag to decide them with:
 * sliding left drops the note, reaching the padlock above the button (and
 * resting on it) keeps the note running hands-free, and anything else sends on
 * release. The decision is a pure function so the sensitivity can be pinned.
 */
class VoiceGestureTest {

    private val threshold = 100f

    @Test
    fun aSmallWobbleArmsNothing() {
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(0f, 0f, threshold, overLock = false))
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(-30f, -40f, threshold, overLock = false))
        // Coming back towards the button re-arms nothing.
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(20f, 30f, threshold, overLock = false))
    }

    @Test
    fun slidingLeftArmsTheCancel() {
        assertEquals(VoiceArm.CANCEL, VoiceGesture.armFor(-120f, 0f, threshold, overLock = false))
        assertEquals(VoiceArm.CANCEL, VoiceGesture.armFor(-threshold, 0f, threshold, overLock = false))
    }

    @Test
    fun onlyThePadlockItselfArmsTheLock() {
        // Distance alone is not a lock any more: the finger has to be on the
        // padlock, however far it has travelled (#364).
        assertEquals(VoiceArm.NONE, VoiceGesture.armFor(0f, -400f, threshold, overLock = false))
        assertEquals(VoiceArm.LOCK, VoiceGesture.armFor(0f, -80f, threshold, overLock = true))
    }

    @Test
    fun aDiagonalUpLeftCancelsRatherThanLocks() {
        // Throwing a note away must be the easier of the two to reach by
        // accident, so the cancel wins when both are true at once.
        assertEquals(VoiceArm.CANCEL, VoiceGesture.armFor(-150f, -150f, threshold, overLock = true))
    }

    @Test
    fun theGestureAsksForADeliberatePress() {
        assertTrue("a tap must not open the microphone", VoiceGesture.HOLD_DELAY_MS >= 200)
        assertTrue(
            "passing over the padlock must not lock: the finger has to rest on it",
            VoiceGesture.LOCK_DWELL_MS >= 250
        )
    }

    // The target is the icon the user can see. The stalk's own bounds are
    // measured on screen, so these numbers describe a stalk whose top-left is
    // at (100, 1000) and which is 52dp wide.
    private val density = 1f
    private val tabLeft = 100f
    private val tabTop = 1000f
    private val tabWidth = VoiceLockTab.WIDTH_DP
    private val iconCentreY = tabTop + VoiceLockTab.ICON_TOP_DP + VoiceLockTab.ICON_SIZE_DP / 2f
    private val iconCentreX = tabLeft + tabWidth / 2f

    private fun at(x: Float, y: Float) =
        VoiceLockTab.reached(x, y, tabLeft, tabTop, tabWidth, density)

    @Test
    fun thePadlockIsTheTarget() {
        assertTrue("the icon itself must lock", at(iconCentreX, iconCentreY))
    }

    @Test
    fun anythingBelowTheIconIsNotTheTarget() {
        assertFalse("the button is not the padlock", at(iconCentreX, tabTop + 200f))
        assertFalse("the chevron under the icon is not it", at(iconCentreX, tabTop + 60f))
        assertFalse("above the stalk is nowhere near it", at(iconCentreX, tabTop - 20f))
    }

    @Test
    fun theTargetIsNoWiderThanTheIcon() {
        val half = VoiceLockTab.ICON_SIZE_DP / 2f
        assertTrue("the icon's own edge is reachable", at(iconCentreX + half, iconCentreY))
        assertFalse("past the stalk's edge must not lock", at(iconCentreX + half + 30f, iconCentreY))
    }

    @Test
    fun anUnmeasuredStalkIsNeverALock() {
        assertFalse(VoiceLockTab.reached(iconCentreX, iconCentreY, 0f, 0f, 0f, density))
    }

    @Test
    fun theDrawnStalkAndTheHitAreaShareTheirGeometry() {
        assertTrue(
            "the icon has to sit inside the stalk that is drawn",
            VoiceLockTab.ICON_TOP_DP + VoiceLockTab.ICON_SIZE_DP <= VoiceLockTab.HEIGHT_DP
        )
    }
}
