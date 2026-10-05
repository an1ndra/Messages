package com.anindra.messages.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pure accessibility helpers, kept out of composables so they stay unit-testable. */
object A11y {
    const val MIN_TOUCH_DP = 48

    /** Interactive controls must expose at least a 48dp touch target. */
    fun touchTarget(current: Dp): Dp = maxOf(current, MIN_TOUCH_DP.dp)

    /** Joins the non-blank parts of a screen-reader description with a period. */
    fun describe(vararg parts: String?): String =
        parts.mapNotNull { it?.trim() }.filter { it.isNotEmpty() }.joinToString(". ")
}

/** An option on the Accessibility screen. */
enum class A11yOption { FONT_SIZE, BOLD, HIGH_CONTRAST, LARGE_TOUCH, REDUCE_MOTION }

/** One option together with where it sits in the screen's run of rows. */
data class A11yOptionRow(val option: A11yOption, val position: RowPosition)

/**
 * Every accessibility option as a single run of rows, so the page reads as one
 * group: the positions run FIRST..LAST with no break in the middle.
 */
val A11Y_OPTION_ROWS: List<A11yOptionRow> = A11yOption.entries.mapIndexed { index, option ->
    val position = when (index) {
        0 -> RowPosition.FIRST
        A11yOption.entries.lastIndex -> RowPosition.LAST
        else -> RowPosition.MIDDLE
    }
    A11yOptionRow(option, position)
}
