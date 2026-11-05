package com.anindra.messages.ui

import androidx.compose.material3.SwipeToDismissBoxValue
import com.anindra.messages.data.SwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compose names its settle values after the row's *start* and *end*, which are
 * layout-direction dependent, while the settings labels and the actions they
 * configure are physical ("Swipe left" always means the finger moves left).
 * Translating the two by name looked correct in LTR and mirrored both the
 * enabled side and the action in RTL (issue #408).
 */
class RtlSwipeMappingTest {

    private val left = SwipeAction.ARCHIVE
    private val right = SwipeAction.DELETE

    private fun actionFor(value: SwipeToDismissBoxValue, isRtl: Boolean): SwipeAction =
        settledSwipeAction(value, isRtl, left, right)

    @Test
    fun startToEndTravelsRightOnlyInLtr() {
        assertTrue(SwipeToDismissBoxValue.StartToEnd.travelsRight(isRtl = false))
        assertFalse(SwipeToDismissBoxValue.StartToEnd.travelsRight(isRtl = true))
    }

    @Test
    fun endToStartTravelsLeftOnlyInLtr() {
        assertFalse(SwipeToDismissBoxValue.EndToStart.travelsRight(isRtl = false))
        assertTrue(SwipeToDismissBoxValue.EndToStart.travelsRight(isRtl = true))
    }

    @Test
    fun theTwoSettleValuesAreOppositeInBothDirections() {
        listOf(false, true).forEach { rtl ->
            assertTrue(
                "in rtl=$rtl the two settle values must travel opposite ways",
                SwipeToDismissBoxValue.StartToEnd.travelsRight(rtl) !=
                    SwipeToDismissBoxValue.EndToStart.travelsRight(rtl)
            )
        }
    }

    @Test
    fun settledDoesNotCountAsATravel() {
        // It is the resting state, never a completed swipe.
        assertFalse(SwipeToDismissBoxValue.Settled.travelsRight(isRtl = false))
        assertFalse(SwipeToDismissBoxValue.Settled.travelsRight(isRtl = true))
    }

    @Test
    fun ltrResolvesTheSwipeByItsPhysicalDirection() {
        assertEquals(right, actionFor(SwipeToDismissBoxValue.StartToEnd, isRtl = false))
        assertEquals(left, actionFor(SwipeToDismissBoxValue.EndToStart, isRtl = false))
    }

    @Test
    fun rtlKeepsThePhysicalDirectionDespiteTheReversedNames() {
        // StartToEnd slides the row to the physical left in RTL, so it is the
        // "Swipe left" gesture and must run the left action, not the right one.
        assertEquals(left, actionFor(SwipeToDismissBoxValue.StartToEnd, isRtl = true))
        assertEquals(right, actionFor(SwipeToDismissBoxValue.EndToStart, isRtl = true))
    }

    @Test
    fun theSameGestureRunsTheSameActionInBothDirections() {
        // A finger moving left is "Swipe left" in every layout direction.
        assertEquals(
            actionFor(SwipeToDismissBoxValue.EndToStart, isRtl = false),
            actionFor(SwipeToDismissBoxValue.StartToEnd, isRtl = true)
        )
        assertEquals(
            actionFor(SwipeToDismissBoxValue.StartToEnd, isRtl = false),
            actionFor(SwipeToDismissBoxValue.EndToStart, isRtl = true)
        )
    }

    @Test
    fun offOnOneSideLeavesTheOtherSwipeableInRtlToo() {
        fun enabledRightOnly(value: SwipeToDismissBoxValue, isRtl: Boolean): Boolean =
            swipeActionFor(value.travelsRight(isRtl), SwipeAction.OFF, right) != SwipeAction.OFF

        // The physical rightward swipe is EndToStart in RTL.
        assertTrue(enabledRightOnly(SwipeToDismissBoxValue.EndToStart, isRtl = true))
        assertFalse(enabledRightOnly(SwipeToDismissBoxValue.StartToEnd, isRtl = true))
    }

    @Test
    fun aLeftwardFingerRevealsThePhysicalRightSide() {
        assertEquals(RevealSide.RIGHT, SwipeDirection.LEFT.revealedSide())
        assertEquals(RevealSide.LEFT, SwipeDirection.RIGHT.revealedSide())
    }

    @Test
    fun theLiveDragIsReadPhysicallyInBothLayouts() {
        // dismissDirection follows the raw offset sign, so its names keep their
        // physical meaning even in RTL. Applying travelsRight() to it showed
        // the mirrored action and colour while swiping (#408).
        assertEquals(right, dragSwipeAction(SwipeToDismissBoxValue.StartToEnd, left, right))
        assertEquals(left, dragSwipeAction(SwipeToDismissBoxValue.EndToStart, left, right))
        assertEquals(SwipeAction.OFF, dragSwipeAction(SwipeToDismissBoxValue.Settled, left, right))
    }

    @Test
    fun theUncoveredSideIsPhysicalAndOppositeTheRow() {
        // A rightward drag leaves the physical left of the row exposed.
        assertEquals(RevealSide.LEFT, dragRevealedSide(SwipeToDismissBoxValue.StartToEnd))
        assertEquals(RevealSide.RIGHT, dragRevealedSide(SwipeToDismissBoxValue.EndToStart))
        assertTrue(dragRevealedSide(SwipeToDismissBoxValue.Settled) == null)
    }

    @Test
    fun theDragAndTheSettledSwipeAgreeOnTheSameGesture() {
        // The physical leftward gesture is EndToStart in the live drag and
        // StartToEnd once settled in RTL; both must run the left action.
        assertEquals(
            dragSwipeAction(SwipeToDismissBoxValue.EndToStart, left, right),
            settledSwipeAction(SwipeToDismissBoxValue.StartToEnd, isRtl = true, left, right)
        )
        assertEquals(
            dragSwipeAction(SwipeToDismissBoxValue.StartToEnd, left, right),
            settledSwipeAction(SwipeToDismissBoxValue.EndToStart, isRtl = true, left, right)
        )
    }

    @Test
    fun revealedSideIsPhysicalSoItDoesNotFlipWithLayoutDirection() {
        // The settings preview draws the block on the side the row slides away
        // from; a leftward swipe must show it on the right in RTL as well as LTR.
        assertEquals(RevealSide.RIGHT, SwipeDirection.LEFT.revealedSide())
    }
}
