package com.anindra.messages.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.anindra.messages.ui.theme.LocalReduceMotion
import com.anindra.messages.ui.theme.Motion
import com.anindra.messages.ui.theme.motionSpring
import com.anindra.messages.ui.theme.motionTween
import com.anindra.messages.AppViewModel
import com.anindra.messages.R
import com.anindra.messages.hideUrls
import com.anindra.messages.data.Conversation
import com.anindra.messages.data.SwipeAction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    vm: AppViewModel,
    onOpenConversation: (Long, String) -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val conversations by remember(vm) { vm.conversations }.collectAsState(initial = emptyList())
    val scheduledAll by vm.scheduledMessages().collectAsState(initial = emptyList())
    val scheduledPreview = remember(scheduledAll) {
        scheduledAll
            .sortedBy { it.timestamp }
            .associate { it.conversationId to it.body }
    }
    val contacts by remember(vm) { vm.contacts }.collectAsState(initial = emptyList())
    val workNums = remember(contacts) {
        contacts.filter { it.workProfile }.map { phoneKey(it.number) }.toSet()
    }
    // Recreated on empty→loaded so the list opens at the top (no restored offset, no anchor jump).
    val listState = remember(conversations.isNotEmpty()) { LazyListState(0, 0) }
    // GM behavior: the stringResource(R.string.access_start_chat) pill collapses to an icon-only FAB as soon as
    // the list scrolls away from the top, and re-expands when it returns.
    val fabExpanded = remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }.value
    var surfacedUnread by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var userScrolledAway by remember { mutableStateOf(false) }
    var unreadSeeded by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var lastBackExitAt by remember { mutableLongStateOf(0L) }
    val syncDone by vm.initialSyncDone.collectAsState()
    val syncProgress by vm.initialSyncProgress.collectAsState()
    // live SMS access state (re-checked on resume)
    var readSmsAllowed by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            readSmsAllowed =
                context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Relative list timestamps ("Now", "5 min") must age while the screen is
    // visible; tick every 30s (and immediately on resume) to recompose them.
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            nowTick = System.currentTimeMillis()
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                nowTick = System.currentTimeMillis()
            }
        }
    }
    val readSmsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        readSmsAllowed = granted
        if (granted) vm.requeryFromSystem()
    }
    var minSkeletonShown by rememberSaveable {
        mutableStateOf(vm.hasLoadedOnce || vm.settings.firstImportDone)
    }
    // skeleton stays while importing; denied SMS access ends it (panel takes over)
    val loaded = minSkeletonShown && (syncDone || !readSmsAllowed)
    var sheetConvoId by remember { mutableLongStateOf(-1L) }
    val sheetConvo = conversations.find { it.id == sheetConvoId }
    var permanentDeleteTarget by remember { mutableStateOf<Conversation?>(null) }
    // Recompute row-level settings whenever any setting changes (SettingsStore is a
    // singleton, so keying on the object would freeze these at first composition).
    val settingsRevision by vm.settings.revision.collectAsState()
    val leftSwipe = vm.settings.swipeLeftAction != SwipeAction.OFF
    val rightSwipe = vm.settings.swipeRightAction != SwipeAction.OFF
    val rowSettings = remember(settingsRevision) {
        RowSettings(
            pinnedEnabled = vm.settings.pinnedEnabled,
            archivingEnabled = vm.settings.archivingEnabled,
            blockingEnabled = vm.settings.blockingEnabled,
            swipeEnabled = leftSwipe || rightSwipe,
            swipeLeftAction = vm.settings.swipeLeftAction,
            swipeRightAction = vm.settings.swipeRightAction,
            hideLinks = vm.settings.hideLinks
        )
    }

    LaunchedEffect(Unit) {
        if (!minSkeletonShown) {
            kotlinx.coroutines.delay(400)
            minSkeletonShown = true
        }
    }
    LaunchedEffect(loaded) {
        if (loaded && !vm.hasLoadedOnce) vm.hasLoadedOnce = true
    }
    // Only a real scroll gesture counts; idle anchor shifts must not flip this.
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }.collect { (scrolling, index, offset) ->
            if (scrolling) userScrolledAway = !(index == 0 && offset == 0)
        }
    }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }
    BackHandler(enabled = !searching && showArchived) {
        showArchived = false
    }
    BackHandler(enabled = !searching && !showArchived) {
        val now = System.currentTimeMillis()
        if (now - lastBackExitAt < 2000) {
            (context as? Activity)?.finish()
        } else {
            lastBackExitAt = now
            Toast.makeText(context, context.getString(R.string.conversations_press_back_exit), Toast.LENGTH_SHORT).show()
        }
    }

    fun moveToTrash(convo: Conversation) {
        if (vm.settings.permanentDeleteEnabled) {
            if (vm.settings.permanentDeleteWarn) {
                permanentDeleteTarget = convo
            } else {
                vm.deleteConversation(convo.id)
            }
            return
        }
        vm.deleteConversation(convo.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.convo_moved_to_trash),
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) vm.restoreFromTrash(convo.id)
        }
    }

    fun archiveWithUndo(convo: Conversation) {
        vm.archiveConversation(convo.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.convo_archived),
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) vm.unarchiveConversation(convo.id)
        }
    }

    /** Snackbar helper so every swipe action is recoverable, like archive/delete. */
    fun withUndo(messageRes: Int, undo: suspend () -> Unit) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(messageRes),
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    fun markReadWithUndo(convo: Conversation) {
        val wasUnread = convo.unreadCount > 0
        if (wasUnread) vm.markRead(convo.id) else vm.markUnread(convo.id)
        withUndo(
            if (wasUnread) R.string.convo_marked_read else R.string.convo_marked_unread
        ) { vm.setRead(convo.id, !wasUnread) }
    }

    fun pinWithUndo(convo: Conversation) {
        val wasPinned = convo.pinned
        if (wasPinned) vm.unpin(convo.id) else vm.togglePin(convo.id)
        withUndo(
            if (wasPinned) R.string.convo_unpinned else R.string.convo_pinned
        ) { vm.setPinned(convo.id, !wasPinned) }
    }

    fun blockWithUndo(convo: Conversation) {
        vm.blockNumber(convo.address)
        withUndo(R.string.convo_blocked) { vm.unblockNumber(convo.address) }
    }

    val showArchiving = vm.settings.archivingEnabled
    val unreadAtTop = vm.settings.unreadAtTopEnabled

    val messageMatchIds = rememberMessageMatchIds(vm, query)

    val displayed = rememberSearchResults(
        conversations, showArchived, query, unreadAtTop, rowSettings.hideLinks, messageMatchIds
    )

    // Reveal a new unread only while the user is still at the top.
    LaunchedEffect(displayed) {
        if (showArchived || query.isNotBlank()) {
            surfacedUnread = displayed.associate { it.id to it.unreadCount }
            unreadSeeded = true
            return@LaunchedEffect
        }
        // Seed on the first real emission; the empty one is just the initial query.
        if (displayed.isEmpty()) return@LaunchedEffect
        val prev = surfacedUnread
        val increased = displayed.firstOrNull { it.unreadCount > (prev[it.id] ?: 0) }
        surfacedUnread = displayed.associate { it.id to it.unreadCount }
        if (unreadSeeded && increased != null && !userScrolledAway) {
            val idx = displayed.indexOfFirst { it.id == increased.id }
            if (idx >= 0) listState.scrollToItem(idx)
        }
        unreadSeeded = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!showArchived) {
                StartChatFab(expanded = fabExpanded, onClick = onNewChat)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searching) {
                    IconButton(onClick = { searching = false; query = "" }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.icon_close_search))
                    }
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.conversations_search)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester)
                    )
                } else {
                    if (showArchived) {
                        IconButton(onClick = { showArchived = false }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.icon_back_to_inbox))
                        }
                        Text(
                            stringResource(R.string.conversations_archived),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            stringResource(R.string.conversations_messages),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (showArchiving) {
                            IconButton(onClick = { showArchived = true }) {
                                Icon(Icons.Rounded.Archive, stringResource(R.string.conversations_archived))
                            }
                        }
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.icon_search))
                        }
                        IconButton(onClick = onOpenSettings) {
                            PersonAvatar(
                                stringResource(R.string.conversations_me), size = 32.dp,
                                backgroundColor = MaterialTheme.colorScheme.primary,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            syncProgress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = context.getString(R.string.access_loading_messages) },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            if (!loaded) {
                ProvideShimmer {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(8, key = { "skeleton_$it" }) { SkeletonConversationRow() }
                        item(key = "spacer") { Spacer(Modifier.height(96.dp)) }
                    }
                }
            } else if (!readSmsAllowed && conversations.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PersonAvatar(
                        "sms-launcher",
                        size = 72.dp,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.conversations_allow_sms_access),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.conversations_sms_permission),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { readSmsPermissionLauncher.launch(Manifest.permission.READ_SMS) }) {
                        Text(stringResource(R.string.conversations_allow_access))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.requeryFromSystem() }) {
                        Text(stringResource(R.string.conversations_retry_loading))
                    }
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }) {
                        Text(stringResource(R.string.conversations_open_settings))
                    }
                }
            } else {
                CompositionLocalProvider(
                    LocalNowTick provides nowTick,
                    LocalScheduledPreview provides scheduledPreview
                ) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(displayed, key = { it.id }) { convo ->
                            SwipeableConversationItem(
                                context,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = motionTween(
                                        reduceMotion = reduceMotion,
                                        durationMs = Motion.DURATION_SHORT4
                                    ),
                                    placementSpec = motionSpring(reduceMotion),
                                    fadeOutSpec = motionTween(
                                        reduceMotion = reduceMotion,
                                        durationMs = Motion.DURATION_SHORT4
                                    )
                                ),
                                settings = rowSettings,
                                swipeEnabled = rowSettings.swipeEnabled && !showArchived,
                                convo = convo,
                                workProfile = workNums.contains(phoneKey(convo.address)),
                                showArchived = showArchived,
                                onClick = {
                                    onOpenConversation(
                                        convo.id,
                                        if (searching) query.trim() else ""
                                    )
                                },
                                onDelete = { moveToTrash(convo) },
                                onArchive = { archiveWithUndo(convo) },
                                onToggleRead = { markReadWithUndo(convo) },
                                onTogglePin = { pinWithUndo(convo) },
                                onBlock = { blockWithUndo(convo) },
                                onLongClick = { sheetConvoId = convo.id }
                            )
                        }
                        item(key = "spacer") { Spacer(Modifier.height(96.dp)) }
                    }
                }
            }
        }
    }

    if (sheetConvo != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetConvoId = -1L },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = if (sheetConvo.name == sheetConvo.address) BidiText.ltr(sheetConvo.display) else sheetConvo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                if (showArchived) {
                    SheetActionRow(Icons.Rounded.Archive, stringResource(R.string.sheet_unarchive), MaterialTheme.colorScheme.primary) {
                        sheetConvoId = -1L; vm.unarchiveConversation(sheetConvo.id)
                    }
                } else {
                    if (rowSettings.pinnedEnabled) {
                        SheetActionRow(
                            Icons.Rounded.PushPin,
                            if (sheetConvo.pinned) stringResource(R.string.sheet_unpin) else stringResource(R.string.sheet_pin),
                            MaterialTheme.colorScheme.primary
                        ) { sheetConvoId = -1L; vm.togglePin(sheetConvo.id) }
                    }
                    if (rowSettings.archivingEnabled) {
                        SheetActionRow(Icons.Rounded.Archive, stringResource(R.string.sheet_archive), MaterialTheme.colorScheme.primary) {
                            sheetConvoId = -1L; archiveWithUndo(sheetConvo)
                        }
                    }
                    SheetActionRow(Icons.Rounded.Delete, stringResource(R.string.sheet_delete), MaterialTheme.colorScheme.error) {
                        sheetConvoId = -1L; moveToTrash(sheetConvo)
                    }
                    if (rowSettings.blockingEnabled) {
                        SheetActionRow(Icons.Rounded.Block, stringResource(R.string.sheet_block), MaterialTheme.colorScheme.error) {
                            sheetConvoId = -1L; vm.blockNumber(sheetConvo.address)
                        }
                    }
                }
            }
        }
    }

    permanentDeleteTarget?.let { convo ->
        PermanentDeleteConfirmDialog(
            onConfirm = { dontShowAgain ->
                if (dontShowAgain) vm.settings.permanentDeleteWarn = false
                permanentDeleteTarget = null
                vm.deleteConversation(convo.id)
            },
            onDismiss = { permanentDeleteTarget = null }
        )
    }
}

@Composable
internal fun SwipeableConversationItem(
    context: android.content.Context,
    settings: RowSettings,
    swipeEnabled: Boolean,
    convo: Conversation,
    workProfile: Boolean,
    showArchived: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit = {},
    onToggleRead: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onBlock: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (swipeEnabled) {
        SwipeConversationItem(context, settings, convo, workProfile, onClick, onDelete, onArchive, onToggleRead, onTogglePin, onBlock, onLongClick, modifier)
    } else {
        ConversationRow(
            modifier = modifier,
            context = context,
            settings = settings,
            convo = convo,
            workProfile = workProfile,
            showArchived = showArchived,
            onClick = onClick,
            onDelete = onDelete,
            onArchive = onArchive,
            onLongClick = onLongClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeConversationItem(
    context: android.content.Context,
    settings: RowSettings,
    convo: Conversation,
    workProfile: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onToggleRead: () -> Unit,
    onTogglePin: () -> Unit,
    onBlock: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dismissStateRef: SwipeToDismissBoxState? = null
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { total -> total * SettingsLayout.SWIPE_COMMIT_FRACTION },
        confirmValueChange = { value ->
            value == SwipeToDismissBoxValue.Settled ||
                    (dismissStateRef?.progress ?: 0f) >= SettingsLayout.SWIPE_COMMIT_FRACTION
        }
    )
    dismissStateRef = dismissState

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val action = direction.action(settings)

            val bgColor by animateColorAsState(
                targetValue = action.background(),
                animationSpec = motionTween(
                    reduceMotion = LocalReduceMotion.current,
                    durationMs = Motion.DURATION_SHORT4
                ),
                label = stringResource(R.string.access_swipe_background)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = SettingsLayout.SWIPE_ICON_INSET),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                }
            ) {
                if (action != SwipeAction.OFF) {
                    Icon(
                        imageVector = action.icon(unread = convo.unreadCount > 0),
                        contentDescription = stringResource(action.a11yLabelRes(convo.unreadCount > 0)),
                        tint = action.iconTint()
                    )
                }
            }
        },
        // A direction set to OFF must not be swipeable at all, not just inert.
        enableDismissFromStartToEnd = swipeActionFor(
            travelsRight = true, settings.swipeLeftAction, settings.swipeRightAction
        ) != SwipeAction.OFF,
        enableDismissFromEndToStart = swipeActionFor(
            travelsRight = false, settings.swipeLeftAction, settings.swipeRightAction
        ) != SwipeAction.OFF
    ) {
        ConversationRow(
            context = context,
            settings = settings,
            convo = convo,
            workProfile = workProfile,
            showArchived = false,
            onClick = onClick,
            onDelete = onDelete,
            onArchive = onArchive,
            onLongClick = onLongClick
        )
    }

    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue.action(settings)) {
            SwipeAction.ARCHIVE -> onArchive()
            SwipeAction.DELETE -> onDelete()
            SwipeAction.MARK_READ_UNREAD -> onToggleRead()
            SwipeAction.PIN -> onTogglePin()
            SwipeAction.BLOCK -> onBlock()
            SwipeAction.OFF -> {}
        }
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
}

/** The action a swipe in [this] direction performs, per the user's configuration. */
private fun SwipeToDismissBoxValue.action(settings: RowSettings): SwipeAction = when (this) {
    SwipeToDismissBoxValue.StartToEnd -> swipeActionFor(
        travelsRight = true, settings.swipeLeftAction, settings.swipeRightAction
    )
    SwipeToDismissBoxValue.EndToStart -> swipeActionFor(
        travelsRight = false, settings.swipeLeftAction, settings.swipeRightAction
    )
    else -> SwipeAction.OFF
}

@Composable
private fun SwipeAction.background() = when (this) {
    SwipeAction.ARCHIVE -> MaterialTheme.colorScheme.secondaryContainer
    SwipeAction.DELETE -> MaterialTheme.colorScheme.error
    SwipeAction.MARK_READ_UNREAD -> MaterialTheme.colorScheme.primary
    SwipeAction.PIN -> MaterialTheme.colorScheme.tertiaryContainer
    SwipeAction.BLOCK -> MaterialTheme.colorScheme.errorContainer
    SwipeAction.OFF -> Color.Transparent
}

@Composable
private fun SwipeAction.iconTint() = when (this) {
    SwipeAction.ARCHIVE -> MaterialTheme.colorScheme.onSecondaryContainer
    SwipeAction.DELETE -> MaterialTheme.colorScheme.onError
    SwipeAction.MARK_READ_UNREAD -> MaterialTheme.colorScheme.onPrimary
    SwipeAction.PIN -> MaterialTheme.colorScheme.onTertiaryContainer
    SwipeAction.BLOCK -> MaterialTheme.colorScheme.onErrorContainer
    SwipeAction.OFF -> Color.Transparent
}

private fun SwipeAction.icon(unread: Boolean): ImageVector = when (this) {
    SwipeAction.ARCHIVE -> Icons.Rounded.Archive
    SwipeAction.DELETE -> Icons.Rounded.Delete
    // Flips with the row so the icon always says what will happen.
    SwipeAction.MARK_READ_UNREAD ->
        if (unread) Icons.Rounded.MarkEmailUnread else Icons.Rounded.MarkEmailRead
    SwipeAction.PIN -> Icons.Rounded.PushPin
    SwipeAction.BLOCK -> Icons.Rounded.Block
    SwipeAction.OFF -> Icons.Rounded.Archive
}

/**
 * A mock conversation row showing what a swipe in [direction] will look like.
 * Shares [background], [icon] and [iconTint] with the real gesture, so the
 * preview cannot drift from the behaviour it advertises.
 */
@Composable
fun SwipeActionPreview(direction: SwipeDirection, action: SwipeAction) {
    val blockAtEnd = direction.revealsTrailingEdge
    val shape = RoundedCornerShape(SettingsLayout.PREVIEW_RADIUS)
    // The action colour is drawn on whichever edge the row slides in from.
    val blockShape = if (blockAtEnd) {
        RoundedCornerShape(topEnd = SettingsLayout.PREVIEW_RADIUS, bottomEnd = SettingsLayout.PREVIEW_RADIUS)
    } else {
        RoundedCornerShape(topStart = SettingsLayout.PREVIEW_RADIUS, bottomStart = SettingsLayout.PREVIEW_RADIUS)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsLayout.PREVIEW_HEIGHT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
    ) {
        if (!blockAtEnd) IconBlock(action, blockShape, atEnd = false)
        // The mock row takes the slack so the colour block always sits flush
        // against the edge the swipe uncovers.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = SettingsLayout.PREVIEW_GAP)
        ) {
            Box(
                Modifier
                    .size(SettingsLayout.PREVIEW_AVATAR)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
            )
            Spacer(Modifier.width(SettingsLayout.PREVIEW_GAP))
            Column {
                Box(
                    Modifier
                        .width(128.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(96.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        if (blockAtEnd) IconBlock(action, blockShape, atEnd = true)
    }
}

/**
 * The revealed colour block with the action's icon. Hidden when there is no
 * action. The icon hugs the row's outer edge, matching the real gesture.
 */
@Composable
private fun RowScope.IconBlock(action: SwipeAction, shape: Shape, atEnd: Boolean) {
    if (action == SwipeAction.OFF) {
        Spacer(Modifier.width(SettingsLayout.PREVIEW_BLOCK_WIDTH))
        return
    }
    Box(
        modifier = Modifier
            .width(SettingsLayout.PREVIEW_BLOCK_WIDTH)
            .fillMaxHeight()
            .clip(shape)
            .background(action.background()),
        contentAlignment = if (atEnd) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Icon(
            imageVector = action.icon(unread = false),
            contentDescription = null,
            tint = action.iconTint(),
            modifier = Modifier
                .padding(horizontal = SettingsLayout.SWIPE_ICON_INSET)
                .size(SettingsLayout.SWIPE_ICON_SIZE)
        )
    }
}

/** Spoken label for the action; [unread] picks the read/unread wording. */
fun SwipeAction.labelRes(unread: Boolean = false): Int = when (this) {
    SwipeAction.ARCHIVE -> R.string.access_archive
    // Matches the long-press sheet: this app has no separate trash feature, so
    // the delete action is always labelled "Delete".
    SwipeAction.DELETE -> R.string.sheet_delete
    // Settings name the action, not its current effect on the row, so the
    // summary stays neutral. The swipe background uses a11yLabelRes() instead.
    SwipeAction.MARK_READ_UNREAD -> R.string.swipe_action_mark_read
    SwipeAction.PIN -> R.string.swipe_action_pin
    SwipeAction.BLOCK -> R.string.swipe_action_block
    SwipeAction.OFF -> R.string.swipe_action_off
}

/** Spoken label for a swipe background, which flips with the row's read state. */
fun SwipeAction.a11yLabelRes(unread: Boolean): Int = when (this) {
    SwipeAction.MARK_READ_UNREAD ->
        if (unread) R.string.access_mark_read else R.string.access_mark_unread
    else -> labelRes()
}

internal data class RowSettings(
    val pinnedEnabled: Boolean,
    /** Legacy rows gate on these; the redesigned list treats them as always on. */
    val draftsEnabled: Boolean = true,
    val reverseSwipe: Boolean = false,
    val archivingEnabled: Boolean,
    val blockingEnabled: Boolean,
    val swipeEnabled: Boolean,
    val swipeLeftAction: SwipeAction = SwipeAction.ARCHIVE,
    val swipeRightAction: SwipeAction = SwipeAction.DELETE,
    val hideLinks: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationRow(
    modifier: Modifier = Modifier,
    context: android.content.Context,
    settings: RowSettings,
    convo: Conversation,
    workProfile: Boolean,
    showArchived: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onArchive: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val now = LocalNowTick.current
    val senderLabel = if (convo.name == convo.address) BidiText.ltr(convo.display) else convo.name
    val hasDraft = convo.draft.isNotBlank()
    val draftLabel = if (settings.hideLinks) hideUrls(convo.draft) else convo.draft
    val snippetLabel = if (settings.hideLinks) hideUrls(convo.snippet) else convo.snippet
    val scheduledLabel = LocalScheduledPreview.current[convo.id]
    val previewLabel = BidiText.isolateNumberRuns(
        if (hasDraft) {
            context.getString(R.string.chat_draft_prefix) + draftLabel
        } else if (!scheduledLabel.isNullOrBlank()) {
            context.getString(R.string.chat_scheduled_prefix) + scheduledLabel
        } else if (convo.isMe && snippetLabel.isNotEmpty()) {
            context.getString(R.string.convo_your_prefix) + snippetLabel
        } else {
            snippetLabel
        }
    ).text
    val a11yLabel = A11y.describe(
        senderLabel,
        previewLabel,
        formatListTime(convo.timestamp, now, context),
        if (convo.unreadCount > 0) context.getString(R.string.access_unread, convo.unreadCount) else null,
        if (convo.pinned && settings.pinnedEnabled) context.getString(R.string.access_pinned) else null
    )
    Box(modifier = modifier.fillMaxWidth()) {
        val pinnedTint = if (convo.pinned && settings.pinnedEnabled) {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.background
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics {
                    contentDescription = a11yLabel
                    role = Role.Button
                    onClick(label = context.getString(R.string.access_open_conversation)) {
                        onClick()
                        true
                    }
                    onLongClick(label = context.getString(R.string.access_conversation_options)) {
                        onLongClick()
                        true
                    }
                }
                .combinedClickable(
                    onClickLabel = context.getString(R.string.access_open_conversation),
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .background(pinnedTint)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonAvatar(convo.groupTitle.ifBlank { convo.address })
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (convo.groupTitle.isNotBlank()) convo.groupTitle
                        else if (convo.name == convo.address) BidiText.ltr(convo.display)
                        else convo.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (convo.pinned && settings.pinnedEnabled) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = stringResource(R.string.access_pinned),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (workProfile) {
                        Spacer(Modifier.width(4.dp))
                        WorkProfileBadge()
                    }
                }
                Spacer(Modifier.height(2.dp))
                if (hasDraft) {
                    Text(
                        text = previewLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = previewLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = if (convo.unreadCount > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatListTime(convo.timestamp, now, context),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (convo.unreadCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                if (convo.unreadCount > 0) UnreadBadge(convo.unreadCount) else Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private enum class ConversationAction {
    Pin, Unpin, Archive, Unarchive, Delete, Block
}

@Composable
internal fun SheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/**
 * GM-style stringResource(R.string.access_start_chat) button: an extended pill while the list is at the top,
 * morphing to an icon-only rounded square as soon as the list scrolls away.
 * The label never wraps: it is clipped by the shrinking width, so "chat"
 * disappears first and "Start" last.
 */
@Composable
internal fun StartChatFab(
    expanded: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val collapsedSize = 56.dp
    // Natural expanded-pill width, captured from the first layout pass (px).
    var naturalWidthPx by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(expanded) {
        progress.animateTo(
            if (expanded) 1f else 0f,
            if (reduceMotion) snap()
            else spring(
                dampingRatio = Motion.SPATIAL_DAMPING_NO_BOUNCY,
                stiffness = Motion.SPATIAL_STIFFNESS_MEDIUM
            )
        )
    }
    val p = progress.value
    val naturalWidthDp = with(LocalDensity.current) { naturalWidthPx.toDp() }
    Row(
        modifier = Modifier
            .height(collapsedSize)
            .then(
                if (naturalWidthPx <= 0) Modifier.widthIn(min = collapsedSize)
                else Modifier.width(lerp(collapsedSize, naturalWidthDp, p))
            )
            .onSizeChanged { if (naturalWidthPx <= 0) naturalWidthPx = it.width }
            .clip(RoundedCornerShape(16.dp))
            .background(cs.secondaryContainer)
            .clickable(onClick = onClick)
            .semantics { contentDescription = context.getString(R.string.access_start_chat) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(16.dp))
        Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = cs.primary)
        if (p > 0.01f) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.access_start_chat),
                style = MaterialTheme.typography.labelLarge,
                color = cs.primary,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(16.dp))
        }
    }
}


