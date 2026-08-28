package com.anindra.messages.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.data.Conversation
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    vm: AppViewModel,
    onOpenConversation: (Long) -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val conversations by vm.conversations.collectAsState(initial = emptyList())
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var lastBackExitAt by remember { mutableLongStateOf(0L) }
    val syncDone by vm.initialSyncDone.collectAsState()
    val syncProgress by vm.initialSyncProgress.collectAsState()
    var minSkeletonShown by rememberSaveable {
        mutableStateOf(vm.hasLoadedOnce || vm.settings.firstImportDone)
    }
    val loaded = minSkeletonShown && syncDone
    var sheetConvoId by remember { mutableLongStateOf(-1L) }
    val sheetConvo = conversations.find { it.id == sheetConvoId }
    val rowSettings = remember(vm.settings) {
        RowSettings(
            pinnedEnabled = vm.settings.pinnedEnabled,
            draftsEnabled = vm.settings.draftsEnabled,
            archivingEnabled = vm.settings.archivingEnabled,
            blockingEnabled = vm.settings.blockingEnabled
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
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    fun moveToTrash(convo: Conversation) {
        vm.deleteConversation(convo.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Conversation moved to trash",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) vm.restoreFromTrash(convo.id)
        }
    }

    fun archiveWithUndo(convo: Conversation) {
        vm.archiveConversation(convo.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Conversation archived",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) vm.unarchiveConversation(convo.id)
        }
    }

    val showArchiving = vm.settings.archivingEnabled

    val displayed = remember(conversations, showArchived, query) {
        conversations.filter { convo ->
            if (showArchived) convo.archived
            else !convo.archived
        }.let { list ->
            if (query.isBlank()) list
            else list.filter {
                it.name.contains(query, true) || it.address.contains(query) ||
                        it.snippet.contains(query, true)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!showArchived) {
                ExtendedFloatingActionButton(
                    onClick = onNewChat,
                    icon = {
                        Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null)
                    },
                    text = { Text("Start chat") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                )
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search")
                    }
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search conversations") },
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
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to inbox")
                        }
                        Text(
                            "Archived",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            "Messages",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (showArchiving) {
                            IconButton(onClick = { showArchived = true }) {
                                Icon(Icons.Rounded.Archive, "Archived")
                            }
                        }
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Outlined.Search, "Search")
                        }
                        IconButton(onClick = onOpenSettings) {
                            PersonAvatar(
                                "me", size = 32.dp,
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
                        .semantics { contentDescription = "Loading messages" },
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
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(displayed, key = { it.id }) { convo ->
                        SwipeableConversationItem(
                            settings = rowSettings,
                            swipeEnabled = vm.settings.swipeActionsEnabled && !showArchived,
                            convo = convo,
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
                    text = if (sheetConvo.name == sheetConvo.address) formatPhoneNumber(sheetConvo.name) else sheetConvo.name,
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
                    SheetActionRow(Icons.Rounded.Archive, "Unarchive", MaterialTheme.colorScheme.primary) {
                        sheetConvoId = -1L; vm.unarchiveConversation(sheetConvo.id)
                    }
                } else {
                    if (rowSettings.pinnedEnabled) {
                        SheetActionRow(
                            Icons.Rounded.PushPin,
                            if (sheetConvo.pinned) "Unpin" else "Pin",
                            MaterialTheme.colorScheme.primary
                        ) { sheetConvoId = -1L; vm.togglePin(sheetConvo.id) }
                    }
                    if (rowSettings.archivingEnabled) {
                        SheetActionRow(Icons.Rounded.Archive, "Archive", MaterialTheme.colorScheme.primary) {
                            sheetConvoId = -1L; archiveWithUndo(sheetConvo)
                        }
                    }
                    SheetActionRow(Icons.Rounded.Delete, "Delete", MaterialTheme.colorScheme.error) {
                        sheetConvoId = -1L; moveToTrash(sheetConvo)
                    }
                    if (rowSettings.blockingEnabled) {
                        SheetActionRow(Icons.Rounded.Block, "Block", MaterialTheme.colorScheme.error) {
                            sheetConvoId = -1L; vm.blockNumber(sheetConvo.address)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableConversationItem(
    settings: RowSettings,
    swipeEnabled: Boolean,
    convo: Conversation,
    showArchived: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    if (swipeEnabled) {
        SwipeConversationItem(settings, convo, onClick, onDelete, onArchive, onLongClick)
    } else {
        ConversationRow(
            settings = settings,
            convo = convo,
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
    settings: RowSettings,
    convo: Conversation,
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

            val bgColor by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                },
                label = "swipe_bg"
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
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Icon(
                            Icons.Rounded.Archive,
                            contentDescription = "Archive",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    else -> {}
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        ConversationRow(
            settings = settings,
            convo = convo,
            showArchived = false,
            onClick = onClick,
            onDelete = onDelete,
            onArchive = onArchive,
            onLongClick = onLongClick
        )
    }

    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onArchive()
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.EndToStart -> {
                onDelete()
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
    val blockingEnabled: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    settings: RowSettings,
    convo: Conversation,
    showArchived: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onArchive: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
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
                        text = if (convo.name == convo.address) formatPhoneNumber(convo.name) else convo.name,
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
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                val hasDraft = settings.draftsEnabled && convo.draft.isNotBlank()
                if (hasDraft) {
                    Text(
                        text = "Draft: ${convo.draft}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    val snippet =
                        if (convo.isMe && convo.snippet.isNotEmpty()) "You: ${convo.snippet}"
                        else convo.snippet
                    Text(
                        text = snippet,
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
                    text = formatListTime(convo.timestamp),
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


