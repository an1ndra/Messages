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
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
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
import com.anindra.messages.AppViewModel
import com.anindra.messages.R
import com.anindra.messages.hideUrls
import com.anindra.messages.data.Conversation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    vm: AppViewModel,
    onOpenConversation: (Long) -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val conversations by remember(vm) { vm.conversations }.collectAsState(initial = emptyList())
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
    val rowSettings = remember(settingsRevision) {
        RowSettings(
            pinnedEnabled = vm.settings.pinnedEnabled,
            draftsEnabled = vm.settings.draftsEnabled,
            archivingEnabled = vm.settings.archivingEnabled,
            blockingEnabled = vm.settings.blockingEnabled,
            swipeEnabled = vm.settings.swipeActionsEnabled,
            reverseSwipe = vm.settings.reverseSwipeEnabled,
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

    val showArchiving = vm.settings.archivingEnabled
    val unreadAtTop = vm.settings.unreadAtTopEnabled

    val displayed = remember(conversations, showArchived, query, unreadAtTop, rowSettings.hideLinks) {
        conversations.filter { convo ->
            if (showArchived) convo.archived
            else !convo.archived
        }.let { list ->
            if (query.isBlank()) list
            else list.filter {
                val snippet = if (rowSettings.hideLinks) hideUrls(it.snippet) else it.snippet
                it.name.contains(query, true) || it.address.contains(query) ||
                        snippet.contains(query, true)
            }
        }.let { list ->
            // Unread-at-top: stable reorder — pinned stays on top, then unread
            // conversations above read ones, timestamp order preserved within a tier.
            if (unreadAtTop && !showArchived) {
                list.sortedWith(
                    compareBy({ !it.pinned }, { if (it.unreadCount > 0) 0 else 1 }, { -it.timestamp })
                )
            } else {
                list
            }
        }
    }

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

            if (!searching && !showArchived) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { searching = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.icon_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.conversations_search),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                CompositionLocalProvider(LocalNowTick provides nowTick) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(displayed, key = { it.id }) { convo ->
                            SwipeableConversationItem(
                                context,
                                settings = rowSettings,
                                swipeEnabled = rowSettings.swipeEnabled && !showArchived,
                                convo = convo,
                                workProfile = workNums.contains(phoneKey(convo.address)),
                                showArchived = showArchived,
                                onClick = { onOpenConversation(convo.id) },
                                onDelete = { moveToTrash(convo) },
                                onArchive = { archiveWithUndo(convo) },
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
                    text = if (sheetConvo.name == sheetConvo.address) sheetConvo.display else sheetConvo.name,
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
private fun SwipeableConversationItem(
    context: android.content.Context,
    settings: RowSettings,
    swipeEnabled: Boolean,
    convo: Conversation,
    workProfile: Boolean,
    showArchived: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    if (swipeEnabled) {
        SwipeConversationItem(context, settings, convo, workProfile, onClick, onDelete, onArchive, onLongClick)
    } else {
        ConversationRow(
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
private fun SwipeConversationItem(
    context: android.content.Context,
    settings: RowSettings,
    convo: Conversation,
    workProfile: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    var dismissStateRef: SwipeToDismissBoxState? = null
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { total -> total * 0.65f },
        confirmValueChange = { value ->
            value == SwipeToDismissBoxValue.Settled ||
                    (dismissStateRef?.progress ?: 0f) >= 0.65f
        }
    )
    dismissStateRef = dismissState

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val endIsDelete = !settings.reverseSwipe

            val bgColor by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.EndToStart ->
                        if (endIsDelete) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondaryContainer
                    SwipeToDismissBoxValue.StartToEnd ->
                        if (endIsDelete) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.error
                    else -> Color.Transparent
                },
                label = stringResource(R.string.access_swipe_background)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                }
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        if (endIsDelete) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.access_delete),
                                tint = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Archive,
                                contentDescription = stringResource(R.string.access_archive),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        if (endIsDelete) {
                            Icon(
                                Icons.Rounded.Archive,
                                contentDescription = stringResource(R.string.access_archive),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.access_delete),
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                    else -> {}
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
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
        val endIsDelete = !settings.reverseSwipe
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                if (endIsDelete) onArchive() else onDelete()
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.EndToStart -> {
                if (endIsDelete) onDelete() else onArchive()
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            else -> {}
        }
    }
}

private data class RowSettings(
    val pinnedEnabled: Boolean,
    val draftsEnabled: Boolean,
    val archivingEnabled: Boolean,
    val blockingEnabled: Boolean,
    val swipeEnabled: Boolean,
    val reverseSwipe: Boolean = false,
    val hideLinks: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
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
    Box(modifier = Modifier.fillMaxWidth()) {
        val pinnedTint = if (convo.pinned && settings.pinnedEnabled) {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.background
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .background(pinnedTint)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonAvatar(convo.address)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (convo.name == convo.address) convo.display else convo.name,
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
                val hasDraft = settings.draftsEnabled && convo.draft.isNotBlank()
                if (hasDraft) {
                    val draft = if (settings.hideLinks) hideUrls(convo.draft) else convo.draft
                    Text(
                        text = stringResource(R.string.chat_draft_prefix) + draft,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    val snippet =
                        if (settings.hideLinks) hideUrls(convo.snippet) else convo.snippet
                    val preview =
                        if (convo.isMe && snippet.isNotEmpty()) stringResource(R.string.convo_your_prefix) + snippet
                        else snippet
                    Text(
                        text = preview,
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
private fun SheetActionRow(
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
private fun StartChatFab(
    expanded: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val collapsedSize = 56.dp
    // Natural expanded-pill width, captured from the first layout pass (px).
    var naturalWidthPx by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        progress.animateTo(
            if (expanded) 1f else 0f,
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
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


