package com.anindra.messages.ui

import com.anindra.messages.data.SwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp
import org.junit.Test

class SwipeDirectionTest {

    @Test
    fun eachDirectionResolvesItsOwnAction() {
        assertEquals(
            SwipeAction.PIN,
            SwipeDirection.LEFT.resolveAction(left = SwipeAction.PIN, right = SwipeAction.BLOCK)
        )
        assertEquals(
            SwipeAction.BLOCK,
            SwipeDirection.RIGHT.resolveAction(left = SwipeAction.PIN, right = SwipeAction.BLOCK)
        )
    }

    @Test
    fun bothDirectionsCanHoldTheSameAction() {
        val both = SwipeAction.ARCHIVE
        assertEquals(both, SwipeDirection.LEFT.resolveAction(both, both))
        assertEquals(both, SwipeDirection.RIGHT.resolveAction(both, both))
    }

    @Test
    fun offInOneDirectionLeavesTheOtherAlone() {
        // The whole point of per-direction actions: disabling one side must not
        // silently disable the other.
        assertEquals(
            SwipeAction.OFF,
            SwipeDirection.LEFT.resolveAction(left = SwipeAction.OFF, right = SwipeAction.DELETE)
        )
        assertEquals(
            SwipeAction.DELETE,
            SwipeDirection.RIGHT.resolveAction(left = SwipeAction.OFF, right = SwipeAction.DELETE)
        )
    }

    @Test
    fun offResolvesToOffSoTheDirectionIsNotSwipeable() {
        SwipeAction.entries.filter { it != SwipeAction.OFF }.forEach { action ->
            assertFalse(
                "a real action must not resolve to OFF",
                SwipeDirection.LEFT.resolveAction(action, action) == SwipeAction.OFF
            )
        }
    }

    @Test
    fun swipingLeftUncoversTheTrailingEdge() {
        // The row travels left, so the revealed colour block is on the right.
        assertTrue(SwipeDirection.LEFT.revealsTrailingEdge)
        assertFalse(SwipeDirection.RIGHT.revealsTrailingEdge)
    }

    @Test
    fun eachDirectionRevealsOppositeEdges() {
        assertTrue(
            SwipeDirection.LEFT.revealsTrailingEdge != SwipeDirection.RIGHT.revealsTrailingEdge
        )
    }

    @Test
    fun legacyDefaultPairResolvesToTheOldBehaviour() {
        // Pre-migration installs were archive-on-one-side, delete-on-the-other;
        // the resolved pair must reproduce that exactly.
        val (left, right) = SwipeAction.legacyPair(enabled = true, reverse = false)
        assertEquals(SwipeAction.ARCHIVE, SwipeDirection.LEFT.resolveAction(left, right))
        assertEquals(SwipeAction.DELETE, SwipeDirection.RIGHT.resolveAction(left, right))
    }

    @Test
    fun previewRoundingReadsAsARectangleNotAPill() {
        val half = SettingsLayout.PREVIEW_HEIGHT / 2
        assertTrue(
            SettingsLayout.PREVIEW_RADIUS < half
        )
    }

    @Test
    fun iconFitsInsideThePreviewBlock() {
        // The block hugs the row edge, so the inset plus the icon on both sides
        // has to fit inside it or the glyph is clipped.
        val needed = SettingsLayout.SWIPE_ICON_INSET * 2 + SettingsLayout.SWIPE_ICON_SIZE
        assertTrue(
            "icon needs $needed but the block is only ${SettingsLayout.PREVIEW_BLOCK_WIDTH}",
            needed <= SettingsLayout.PREVIEW_BLOCK_WIDTH
        )
    }

    @Test
    fun previewLeavesRoomForTheMockRowOnANarrowScreen() {
        // Narrowest supported width is 360dp. The colour block, the avatar and
        // the insets must all fit, or the mock row collapses on small screens.
        val narrowest = 360.dp - SettingsLayout.SCREEN_PADDING * 2 - SettingsLayout.PREVIEW_INSET * 2
        val used = SettingsLayout.PREVIEW_BLOCK_WIDTH + SettingsLayout.PREVIEW_AVATAR +
            SettingsLayout.PREVIEW_GAP * 2
        assertTrue(
            "preview needs $used but only $narrowest is available",
            used < narrowest
        )
    }
}
