package com.anindra.messages.ui

import com.anindra.messages.data.SwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings rows are labelled "Swipe left" / "Swipe right" by the direction
 * the finger travels, while Compose names its settle values after the direction
 * the row travels. Wiring the two by name swapped them, so choosing "Swipe
 * left" performed the action configured under "Swipe right" — and the preview
 * drawn in settings advertised the opposite of what the gesture did.
 */
class SwipeDirectionMappingTest {

    private val left = SwipeAction.ARCHIVE
    private val right = SwipeAction.DELETE

    @Test
    fun aRightwardSwipeRunsTheActionLabelledSwipeRight() {
        // StartToEnd is a rightward swipe: the row moves toward the end edge.
        assertEquals(right, swipeActionFor(travelsRight = true, left, right))
    }

    @Test
    fun aLeftwardSwipeRunsTheActionLabelledSwipeLeft() {
        assertEquals(left, swipeActionFor(travelsRight = false, left, right))
    }

    @Test
    fun theTwoDirectionsRunDifferentActions() {
        // The swap was only ever visible when the two differed, which is why
        // matching defaults hid it.
        assertFalse(
            swipeActionFor(travelsRight = true, left, right) ==
                swipeActionFor(travelsRight = false, left, right)
        )
    }

    @Test
    fun offMeansThatDirectionIsNotSwipeable() {
        assertTrue(swipeActionFor(travelsRight = false, SwipeAction.OFF, right) == SwipeAction.OFF)
        assertTrue(swipeActionFor(travelsRight = true, left, SwipeAction.OFF) == SwipeAction.OFF)
    }

    @Test
    fun offOnOneSideLeavesTheOtherSwipeable() {
        assertEquals(
            right,
            swipeActionFor(travelsRight = true, SwipeAction.OFF, right)
        )
        assertEquals(
            left,
            swipeActionFor(travelsRight = false, left, SwipeAction.OFF)
        )
    }

    @Test
    fun theSettingsLabelResolvesToTheSameActionTheGestureRuns() {
        // SwipeDirection.LEFT is what the "Swipe left" row edits, and its preview
        // draws the block on the right-hand edge, i.e. a leftward swipe. The
        // gesture has to agree with both.
        assertEquals(
            SwipeDirection.LEFT.resolveAction(left, right),
            swipeActionFor(travelsRight = false, left, right)
        )
        assertEquals(
            SwipeDirection.RIGHT.resolveAction(left, right),
            swipeActionFor(travelsRight = true, left, right)
        )
    }
}