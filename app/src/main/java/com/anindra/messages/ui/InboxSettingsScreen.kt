package com.anindra.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.R
import com.anindra.messages.data.SwipeAction


/**
 * Trailing hint that the whole row opens the picker. The row already looks
 * tappable and the row's own click handler does the work, so this is a label
 * rather than a second control.
 */
@Composable
internal fun ChangeAffordance() {
    Text(
        text = stringResource(R.string.settings_swipe_actions_change),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Per-conversation behaviour, split out of General settings so the top level
 * stays short. Every row is a switch with no description, matching the rest of
 * the settings list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxSettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    scrollState: ScrollState = rememberScrollState()
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val revision by vm.settings.revision.collectAsState()

    var archiving by remember(revision) { mutableStateOf(vm.settings.archivingEnabled) }
    var pinned by remember(revision) { mutableStateOf(vm.settings.pinnedEnabled) }
    var leftAction by remember(revision) { mutableStateOf(vm.settings.swipeLeftAction) }
    var rightAction by remember(revision) { mutableStateOf(vm.settings.swipeRightAction) }
    var pickingDirection by remember { mutableStateOf<SwipeDirection?>(null) }
    var unreadAtTop by remember(revision) { mutableStateOf(vm.settings.unreadAtTopEnabled) }
    var forwarding by remember(revision) { mutableStateOf(vm.settings.forwardingEnabled) }
    var delayed by remember(revision) { mutableStateOf(vm.settings.delayedSendingEnabled) }
    var delaySeconds by remember(revision) { mutableIntStateOf(vm.settings.delaySeconds) }
    var delayDialog by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }
    val blockedNumbers by vm.blockedNumbers().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_inbox_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.icon_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = SettingsLayout.SCREEN_PADDING)
        ) {
            Spacer(Modifier.height(SettingsLayout.TOP_GAP))

            SettingsGroup {
                SettingsRow(
                    position = RowPosition.FIRST,
                    title = stringResource(R.string.settings_archiving_title),
                    checked = archiving,
                    onChecked = { archiving = it; vm.settings.archivingEnabled = it }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_pinned_title),
                    checked = pinned,
                    onChecked = { pinned = it; vm.settings.pinnedEnabled = it; if (!it) vm.unpinAll() }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.swipe_left_title),
                    subtitle = stringResource(leftAction.labelRes()),
                    onClick = { pickingDirection = SwipeDirection.LEFT },
                    trailing = { ChangeAffordance() },
                    preview = { SwipeActionPreview(SwipeDirection.LEFT, leftAction) }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.swipe_right_title),
                    subtitle = stringResource(rightAction.labelRes()),
                    onClick = { pickingDirection = SwipeDirection.RIGHT },
                    trailing = { ChangeAffordance() },
                    preview = { SwipeActionPreview(SwipeDirection.RIGHT, rightAction) }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_unread_top_title),
                    checked = unreadAtTop,
                    onChecked = { unreadAtTop = it; vm.settings.unreadAtTopEnabled = it }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_forwarding_title),
                    checked = forwarding,
                    onChecked = { forwarding = it; vm.settings.forwardingEnabled = it }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_blocking_title),
                    subtitle = context.getString(
                        R.string.settings_blocked_numbers_count, blockedNumbers.size
                    ),
                    onClick = { showBlocked = true }
                )
                SettingsRow(
                    position = if (delayed) RowPosition.MIDDLE else RowPosition.LAST,
                    title = stringResource(R.string.settings_delayed_title),
                    checked = delayed,
                    onChecked = {
                        delayed = it; vm.settings.delayedSendingEnabled = it
                        if (it) delayDialog = true
                    }
                )
                if (delayed) {
                    SettingsRow(
                        position = RowPosition.LAST,
                        title = stringResource(R.string.settings_delay_secs_title),
                        subtitle = String.format(
                            context.getString(R.string.settings_delay_with_value), delaySeconds
                        ),
                        onClick = { delayDialog = true }
                    )
                }
            }

            SettingsFooter(stringResource(R.string.settings_inbox_footer))
        }
    }

    if (showBlocked) {
        AlertDialog(
            onDismissRequest = { showBlocked = false },
            title = { Text(stringResource(R.string.settings_blocked_numbers_title)) },
            text = {
                Column {
                    if (blockedNumbers.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_blocked_numbers_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        blockedNumbers.forEach { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    formatPhoneNumber(entry.number),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { vm.unblockNumber(entry.number) }) {
                                    Text(stringResource(R.string.common_unblock))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlocked = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }

    pickingDirection?.let { dir ->
        SwipeActionDialog(
            current = if (dir == SwipeDirection.LEFT) leftAction else rightAction,
            onPick = { picked ->
                if (dir == SwipeDirection.LEFT) {
                    leftAction = picked
                    vm.settings.swipeLeftAction = picked
                } else {
                    rightAction = picked
                    vm.settings.swipeRightAction = picked
                }
                pickingDirection = null
            },
            onDismiss = { pickingDirection = null }
        )
    }

    if (delayDialog) {
        val options = listOf(1, 3, 5, 10, 30)
        AlertDialog(
            onDismissRequest = { delayDialog = false },
            title = { Text(stringResource(R.string.settings_pin_delay)) },
            text = {
                Column {
                    options.forEach { secs ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = delaySeconds == secs,
                                onClick = {
                                    delaySeconds = secs
                                    vm.settings.delaySeconds = secs
                                    delayDialog = false
                                }
                            )
                            Text(
                                text = context.getString(R.string.settings_countdown_placeholder, secs),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { delayDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeActionDialog(
    current: SwipeAction,
    onPick: (SwipeAction) -> Unit,
    onDismiss: () -> Unit
) {
    val options = SwipeAction.entries
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                // OFF is always offered: it is how a direction gets disabled now
                // that there is no separate master switch.
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            // The whole row is the choice, not just the radio, so
                            // tapping the label works.
                            .selectable(
                                selected = current == option,
                                onClick = { onPick(option) }
                            )
                            // 2.dp left the six options ~25dp apart, so each
                            // row's touch target overlapped its neighbours and
                            // the list read as crowded. A choice row wants the
                            // standard minimum.
                            .heightIn(min = A11y.MIN_TOUCH_DP.dp)
                    ) {
                        RadioButton(selected = current == option, onClick = null)
                        Text(
                            text = stringResource(option.labelRes()),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}
