package com.anindra.messages.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anindra.messages.data.SwipeAction

/**
 * Geometry for the settings list, kept out of the composables so the spacing
 * contract is unit-testable.
 *
 * A group is a run of separate row cards with a small gap between them. The
 * group's *outer* corners are strongly rounded while the corners facing another
 * row are barely rounded, so the run reads as one soft-edged group rather than
 * as a stack of unrelated cards. Rows show a value as their subtitle only where
 * there is a value to show; plain toggles carry no description.
 */
object SettingsLayout {
    /** Outer inset of the group column from the screen edge. */
    val SCREEN_PADDING: Dp = 12.dp

    /**
     * Gap between rows inside a group. Half the old 4dp: Google Messages runs its
     * settings rows together, and at 4dp the band of page either side of every
     * row was wide enough to read as separate list items again.
     */
    val ROW_GAP: Dp = 2.dp

    /** Gap between groups, which reads as a section break. */
    val GROUP_GAP: Dp = 14.dp

    /**
     * Gap below the app bar, before the first group. Tighter than [GROUP_GAP]:
     * this one is "below the title", not "between sections", and at group size
     * it reads as an empty band under the app bar.
     */
    val TOP_GAP: Dp = 8.dp

    /** Inset of the row's text and trailing control from the card edge. */
    val ROW_CONTENT_PADDING: Dp = 18.dp

    /**
     * Horizontal inset for a footer note. Zero on purpose: the note shares the
     * left and right edges of the row cards, so it lines up with the group's
     * border rather than with the text inside each card.
     */
    val FOOTER_HORIZONTAL_PADDING: Dp = 0.dp

    /**
     * Space above a footer note. Tighter than [GROUP_GAP]: the note belongs to
     * the group above it, so a full section gap would read as a new section.
     */
    val FOOTER_TOP_GAP: Dp = 6.dp

    /**
     * Space below a footer note. Only needs to clear the bottom system bar, so
     * it is much smaller than the top gap.
     */
    val FOOTER_BOTTOM_GAP: Dp = 20.dp

    /**
     * Rounding on a row's outward-facing corners, i.e. the group's first and
     * last rows. Raised from 7dp: with a 2dp row gap the run reads as a stack of
     * near-rectangles, and softening the two outer ends is what makes it read as
     * one rounded block.
     */
    val OUTER_RADIUS: Dp = 12.dp

    /**
     * Rounding on a row's corners that face a neighbouring row: well under half
     * the outer value, so the group's ends are still the softest part of the run.
     */
    val INNER_RADIUS: Dp = 4.dp

    /** Height of a row that carries only a title. */
    val ROW_HEIGHT_TITLE_ONLY: Dp = 56.dp

    /** Height of a row that also carries a description. */
    val ROW_HEIGHT_WITH_SUBTITLE: Dp = 76.dp

    /** Extra height when large touch targets are on. */
    val LARGE_TARGET_GROWTH: Dp = 12.dp

    /** Height of the mock conversation row shown as a swipe action's preview. */
    val PREVIEW_HEIGHT: Dp = 84.dp

    /** Rounding of the preview mock and of the colour block revealed by a swipe. */
    val PREVIEW_RADIUS: Dp = 6.dp

    /** Width of the revealed colour block in the preview. */
    val PREVIEW_BLOCK_WIDTH: Dp = 96.dp

    /** Gap between the preview's colour block, avatar and text bars. */
    val PREVIEW_GAP: Dp = 16.dp

    /** Inset of the preview mock from the row card's edge. */
    val PREVIEW_INSET: Dp = 16.dp

    /** Diameter of the preview's placeholder avatar. */
    val PREVIEW_AVATAR: Dp = 40.dp

    /** Side of the swipe action's icon. */
    val SWIPE_ICON_SIZE: Dp = 24.dp

    /**
     * Inset of the action icon from the row's leading edge. Shared by the real
     * gesture and its preview so the preview cannot advertise a different
     * position than the swipe actually uses.
     */
    val SWIPE_ICON_INSET: Dp = 24.dp


    /**
     * A title-only row is shorter than one with a description, so the list does
     * not read as a column of identical slabs. Both still clear the 48dp touch
     * target minimum, and both grow when large touch targets are on.
     */
    fun rowMinHeight(hasSubtitle: Boolean, largeTouchTargets: Boolean): Dp {
        val base = if (hasSubtitle) ROW_HEIGHT_WITH_SUBTITLE else ROW_HEIGHT_TITLE_ONLY
        return (if (largeTouchTargets) base + LARGE_TARGET_GROWTH else base)
            .coerceAtLeast(A11y.MIN_TOUCH_DP.dp)
    }
}

/** Which way a conversation row is swiped, and so which configured action runs. */
enum class SwipeDirection { LEFT, RIGHT }

/**
 * The action a swipe in this direction performs. A direction set to
 * [SwipeAction.OFF] resolves to OFF, which is what makes it unswipeable.
 */
fun SwipeDirection.resolveAction(left: SwipeAction, right: SwipeAction): SwipeAction = when (this) {
    SwipeDirection.LEFT -> left
    SwipeDirection.RIGHT -> right
}

/**
 * True when the revealed colour block sits at the *end* of the row. Swiping left
 * slides the row away to the left, uncovering its right-hand edge, and vice
 * versa -- so the block always appears on the side the row travels towards.
 */
val SwipeDirection.revealsTrailingEdge: Boolean
    get() = this == SwipeDirection.LEFT

/** Where a row sits in its group, which decides which of its corners soften. */
enum class RowPosition { FIRST, MIDDLE, LAST, SINGLE }

/** A row's four corner radii, resolved from its [RowPosition]. */
data class RowCorners(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp
)

/**
 * A group's outer corners are strongly rounded and the corners facing a
 * neighbouring row are barely rounded, so a run of separate cards with gaps
 * between them still reads as one soft-edged group.
 */
fun rowCorners(position: RowPosition): RowCorners {
    val outer = SettingsLayout.OUTER_RADIUS
    val inner = SettingsLayout.INNER_RADIUS
    return when (position) {
        RowPosition.FIRST -> RowCorners(outer, outer, inner, inner)
        RowPosition.MIDDLE -> RowCorners(inner, inner, inner, inner)
        RowPosition.LAST -> RowCorners(inner, inner, outer, outer)
        RowPosition.SINGLE -> RowCorners(outer, outer, outer, outer)
    }
}
