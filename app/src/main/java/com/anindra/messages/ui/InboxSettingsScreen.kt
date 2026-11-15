package com.anindra.messages.ui

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.anindra.messages.data.CategoryRule
import com.anindra.messages.data.InboxCategory
import com.anindra.messages.data.InboxCategoryRules
import com.anindra.messages.data.RuleKind
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
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
    var showRules by remember { mutableStateOf(false) }
    val blockedNumbers by vm.blockedNumbers().collectAsState(initial = emptyList())

    if (showRules) {
        InboxCategoryRulesScreen(vm, onBack = { showRules = false })
        return
    }

    Scaffold(
        // Same sheet as General settings: page and app bar are surfaceContainer,
        // rows are inset at the page background.
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
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
                    title = stringResource(R.string.settings_inbox_rules_title),
                    subtitle = stringResource(R.string.settings_inbox_rules_subtitle),
                    onClick = { showRules = true }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
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
                    title = stringResource(R.string.settings_pinch_zoom_title),
                    checked = vm.settings.pinchZoomEnabled,
                    onChecked = { vm.settings.pinchZoomEnabled = it }
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
        BlockedNumbersDialog(
            blockedNumbers = blockedNumbers,
            onUnblock = vm::unblockNumber,
            onDismiss = { showBlocked = false }
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
                    DialogText(
                        stringResource(R.string.settings_choose_delay),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    options.forEach { secs ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    delaySeconds = secs
                                    vm.settings.delaySeconds = secs
                                    delayDialog = false
                                }
                                .heightIn(min = A11y.MIN_TOUCH_DP.dp)
                        ) {
                            RadioButton(
                                selected = delaySeconds == secs,
                                onClick = {
                                    delaySeconds = secs
                                    vm.settings.delaySeconds = secs
                                    delayDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = context.getString(R.string.settings_countdown_placeholder, secs)
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

/** A word — or a sender — the user routes to one of the inbox tabs.
 *
 *  Rules are consulted before the app's own guesses, so "anything from
 *  AD-AMAZON is Spam" means exactly that. Saving or deleting a rule re-files the
 *  threads it matches straight away instead of waiting for their next message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InboxCategoryRulesScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    scrollState: ScrollState = rememberScrollState()
) {
    BackHandler(onBack = onBack)
    val rules by vm.categoryRules.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                title = { Text(stringResource(R.string.settings_inbox_rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.icon_back))
                    }
                },
                actions = {
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.inbox_rules_add))
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
            // Nothing is drawn until the table has been read once: an empty
            // "no rules yet" under every tab was mistaken for rules going missing.
            val byCategory = remember(rules) { rules?.let { InboxCategoryRules.group(it) } }
            byCategory?.forEach { (category, inTab) ->
                RuleSection(
                    category = category,
                    rules = inTab,
                    onDelete = { vm.deleteCategoryRule(it) }
                )
                Spacer(Modifier.height(12.dp))
            }
            SettingsFooter(stringResource(R.string.settings_inbox_rules_subtitle))
        }
    }

    if (adding) {
        ModalBottomSheet(
            onDismissRequest = { adding = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            AddCategoryRuleForm(
                onCancel = { adding = false },
                onAdd = { kind, pattern, category ->
                    vm.addCategoryRule(kind, pattern, category)
                    adding = false
                }
            )
        }
    }
}

/** The new-rule form: how to match at the top, the word in the field, the four
 *  tabs at the bottom — the order the questions are asked in, and the whole form
 *  stays above the keyboard while the word is typed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddCategoryRuleForm(
    onCancel: () -> Unit,
    onAdd: (RuleKind, String, InboxCategory) -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(RuleKind.KEYWORD) }
    var category by remember { mutableStateOf(InboxCategory.SPAM) }
    val focus = remember { FocusRequester() }
    val formScroll = rememberScrollState()

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(formScroll)
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.inbox_rules_add),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.chat_cancel)) }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            RuleKind.entries.forEachIndexed { index, candidate ->
                SegmentedButton(
                    selected = kind == candidate,
                    onClick = { kind = candidate },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = RuleKind.entries.size
                    )
                ) {
                    Text(
                        stringResource(
                            if (candidate == RuleKind.KEYWORD) {
                                R.string.inbox_rules_kind_keyword
                            } else {
                                R.string.inbox_rules_kind_sender
                            }
                        )
                    )
                }
            }
        }
        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            label = { Text(stringResource(R.string.inbox_rules_pattern_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
        )
        SettingsGroup {
            InboxCategory.entries.forEachIndexed { index, candidate ->
                SettingsRow(
                    position = when {
                        InboxCategory.entries.size == 1 -> RowPosition.SINGLE
                        index == 0 -> RowPosition.FIRST
                        index == InboxCategory.entries.lastIndex -> RowPosition.LAST
                        else -> RowPosition.MIDDLE
                    },
                    title = stringResource(candidate.labelRes),
                    onClick = { category = candidate },
                    trailing = {
                        RadioButton(selected = category == candidate, onClick = null)
                    }
                )
            }
        }
        Button(
            onClick = { onAdd(kind, pattern, category) },
            enabled = pattern.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) { Text(stringResource(R.string.action_add)) }
    }
}


/** One tab's rules, drawn as the settings rows every other page uses: the tab
 *  is the first row of its own group and its rules follow, so the page reads
 *  like the rest of the settings instead of a bespoke list. */
@Composable
internal fun RuleSection(
    category: InboxCategory,
    rules: List<CategoryRule>,
    onDelete: (Long) -> Unit
) {
    SettingsGroup {
        SettingsRow(
            position = if (rules.isEmpty()) RowPosition.SINGLE else RowPosition.FIRST,
            title = stringResource(category.labelRes),
            subtitle = if (rules.isEmpty()) stringResource(R.string.inbox_rules_empty) else null
        )
        rules.forEachIndexed { index, rule ->
            SettingsRow(
                position = if (index == rules.lastIndex) RowPosition.LAST else RowPosition.MIDDLE,
                title = rule.pattern,
                subtitle = stringResource(
                    if (rule.kind == RuleKind.KEYWORD) {
                        R.string.inbox_rules_kind_keyword
                    } else {
                        R.string.inbox_rules_kind_sender
                    }
                ),
                trailing = {
                    IconButton(onClick = { onDelete(rule.id) }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.access_delete))
                    }
                }
            )
        }
    }
}
