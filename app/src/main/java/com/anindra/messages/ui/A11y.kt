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
