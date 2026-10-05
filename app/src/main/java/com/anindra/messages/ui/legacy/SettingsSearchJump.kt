package com.anindra.messages.ui.legacy

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import com.anindra.messages.ui.SettingsSearch
import kotlinx.coroutines.delay

/**
 * Title of the option a search result just jumped to, so the row can flash
 * itself. Read inside the row rather than passed in, so pointing at an option
 * does not mean editing every call site on a screen.
 */
val LocalHighlightedSetting = compositionLocalOf<String?> { null }

/**
 * Where each card landed, keyed by its index in
 * [SettingsSearch.LegacySettingsIndex]. Card indexes are unique across the whole
 * settings tree, so one map serves every screen.
 */
val LocalCardOffsets = compositionLocalOf<MutableMap<Int, Int>> { mutableMapOf() }

/**
 * A settings card that reports its own position, so a jump can scroll to it.
 *
 * Delegates to [SettingsGroup] rather than drawing its own card: an earlier
 * version wrapped the rows in a Box, which stacks its children and drew every
 * row on top of the one before it.
 */
@Composable
fun SettingsCard(card: Int, content: @Composable () -> Unit) {
    val offsets = LocalCardOffsets.current
    SettingsGroup(
        modifier = Modifier.onGloballyPositioned {
            offsets[card] = it.positionInParent().y.toInt()
        },
        content = content
    )
}

/**
 * Scrolls to the card holding [pendingRowRes] and flashes that row, then reports
 * back so the caller can clear the pending target.
 *
 * Runs after the target screen has been laid out, so the card's offset is polled
 * for rather than assumed: bringIntoView() proved unreliable here and silently
 * left the target off-screen with no indication it had moved.
 */
@Composable
fun SettingsJumpEffect(
    pendingRowRes: Int?,
    cardOffsets: MutableMap<Int, Int>,
    scrollState: ScrollState,
    setHighlight: (String?) -> Unit,
    onHandled: () -> Unit
) {
    val pendingTitle = pendingRowRes?.let { stringResource(it) }
    LaunchedEffect(pendingRowRes, pendingTitle) {
        val target = pendingRowRes ?: return@LaunchedEffect
        val card = SettingsSearch.cardOf(target)
        for (attempt in 0 until 15) {
            val y = cardOffsets[card]
            if (y != null && scrollState.maxValue > 0) {
                scrollState.animateScrollTo(y)
                break
            }
            delay(50)
        }
        setHighlight(pendingTitle)
        delay(1400)
        setHighlight(null)
        onHandled()
    }
}