package com.anindra.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.anindra.messages.R
import com.anindra.messages.data.SwipeAction

/**
 * Both swipe directions on their own page. Inbox settings now carries a single
 * "Swipe actions" row; the per-direction choices live here, in the same rows as
 * the rest of settings, and write straight through to the store.
 */
@Composable
internal fun SwipeActionsScreen(
    leftAction: SwipeAction,
    rightAction: SwipeAction,
    onLeftChange: (SwipeAction) -> Unit,
    onRightChange: (SwipeAction) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var pickingDirection by remember { mutableStateOf<SwipeDirection?>(null) }

    SettingsScaffold(
        title = stringResource(R.string.settings_swipe_actions_title),
        onBack = onBack
    ) {
        SettingsGroup {
            SettingsRow(
                position = RowPosition.FIRST,
                title = stringResource(R.string.swipe_left_title),
                subtitle = stringResource(leftAction.labelRes()),
                onClick = { pickingDirection = SwipeDirection.LEFT },
                trailing = { ChangeAffordance() },
                preview = { SwipeActionPreview(SwipeDirection.LEFT, leftAction) }
            )
            SettingsRow(
                position = RowPosition.LAST,
                title = stringResource(R.string.swipe_right_title),
                subtitle = stringResource(rightAction.labelRes()),
                onClick = { pickingDirection = SwipeDirection.RIGHT },
                trailing = { ChangeAffordance() },
                preview = { SwipeActionPreview(SwipeDirection.RIGHT, rightAction) }
            )
        }
    }

    pickingDirection?.let { direction ->
        SwipeActionDialog(
            current = if (direction == SwipeDirection.LEFT) leftAction else rightAction,
            onPick = { picked ->
                if (direction == SwipeDirection.LEFT) onLeftChange(picked) else onRightChange(picked)
                pickingDirection = null
            },
            onDismiss = { pickingDirection = null }
        )
    }
}
