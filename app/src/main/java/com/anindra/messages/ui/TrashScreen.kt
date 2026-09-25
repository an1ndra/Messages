package com.anindra.messages.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.R
import com.anindra.messages.data.Conversation
import com.anindra.messages.data.TrashReason
import com.anindra.messages.data.TrashedMessage
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val conversations by vm.trashedConversations().collectAsState(initial = emptyList())
    val messages by vm.trashedMessages().collectAsState(initial = emptyList())
    val context = LocalContext.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var trashDays by remember(vm.settings.revision) { mutableStateOf(vm.settings.retentionTrashDays) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showDeleteForeverDialog by remember { mutableStateOf(false) }
    var deleteConversationTarget by remember { mutableStateOf<Long?>(null) }
    var deleteMessageTarget by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.icon_back))
                    }
                },
                actions = {
                    AutoDeleteDurationAction(
                        days = trashDays,
                        active = vm.settings.retentionEnabled && vm.settings.retentionTrash,
                        onPick = { vm.settings.retentionTrashDays = it; trashDays = it }
                    )
                    if (conversations.isNotEmpty() || messages.isNotEmpty()) {
                        TextButton(onClick = { showEmptyTrashDialog = true }) {
                            Text(stringResource(R.string.trash_empty), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ExpressiveTabs(
                tabs = listOf(
                    ExpressiveTab(R.string.trash_tab_conversations),
                    ExpressiveTab(R.string.trash_tab_messages)
                ),
                selected = tab,
                onSelect = { tab = it }
            )
            if (tab == 0) {
                if (conversations.isEmpty()) {
                    EmptyTrash(
                        title = stringResource(R.string.trash_empty_is_empty),
                        message = stringResource(R.string.trash_empty_message)
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                    ) {
                        items(conversations, key = { it.id }) { convo ->
                            TrashRow(
                                convo = convo,
                                onRestore = { vm.restoreFromTrash(convo.id) },
                                onDeleteForever = {
                                    deleteConversationTarget = convo.id
                                    showDeleteForeverDialog = true
                                }
                            )
                        }
                    }
                }
            } else {
                if (messages.isEmpty()) {
                    EmptyTrash(
                        title = stringResource(R.string.trash_messages_empty),
                        message = stringResource(R.string.trash_messages_empty_message)
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            TrashMessageRow(
                                msg = msg,
                                onRestore = { vm.restoreMessage(msg.id) },
                                onDeleteForever = {
                                    deleteMessageTarget = msg.id
                                    showDeleteForeverDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showEmptyTrashDialog) {
            AlertDialog(
                onDismissRequest = { showEmptyTrashDialog = false },
                title = { Text(stringResource(R.string.trash_empty)) },
                text = {
                    Text(
                        String.format(
                            context.getString(R.string.trash_empty_confirm),
                            conversations.size + messages.size
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.emptyTrash()
                        vm.emptyMessageTrash()
                        showEmptyTrashDialog = false
                        Toast.makeText(context, context.getString(R.string.trash_emptied), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.trash_empty), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyTrashDialog = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        if (showDeleteForeverDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteForeverDialog = false
                    deleteConversationTarget = null
                    deleteMessageTarget = null
                },
                title = {
                    Text(
                        stringResource(
                            if (deleteMessageTarget != null) R.string.trash_delete_message
                            else R.string.trash_delete_conversation
                        )
                    )
                },
                text = {
                    val name = when {
                        deleteMessageTarget != null -> messages.find { it.id == deleteMessageTarget }?.body
                        else -> conversations.find { it.id == deleteConversationTarget }?.name
                    }.orEmpty()
                    Text(String.format(context.getString(R.string.trash_delete_forever_confirm), name))
                },
                confirmButton = {
                    TextButton(onClick = {
                        deleteConversationTarget?.let { vm.deleteForever(it) }
                        deleteMessageTarget?.let { vm.deleteMessageForever(it) }
                        showDeleteForeverDialog = false
                        deleteConversationTarget = null
                        deleteMessageTarget = null
                        Toast.makeText(context, context.getString(R.string.toast_trash_deleted), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteForeverDialog = false
                        deleteConversationTarget = null
                        deleteMessageTarget = null
                    }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun EmptyTrash(title: String, message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.DeleteOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TrashRow(
    convo: Conversation,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(convo.address)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = convo.name.ifBlank { BidiText.ltr(convo.display) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (TrashReason.isBlockedKeyword(convo.deletedReason)) {
                    TrashReasonTag()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = String.format(
                        LocalContext.current.getString(R.string.trash_deleted_on),
                        formatTrashDate(convo.deletedAt)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onRestore) {
            Icon(
                Icons.Rounded.RestoreFromTrash,
                contentDescription = stringResource(R.string.access_restore),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDeleteForever) {
            Icon(
                Icons.Rounded.DeleteForever,
                contentDescription = stringResource(R.string.access_delete_forever),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TrashMessageRow(
    msg: TrashedMessage,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val sender = if (msg.name == msg.address) BidiText.ltr(formatPhoneNumber(msg.address)) else msg.name
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(msg.address)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = sender,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = msg.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = String.format(
                    LocalContext.current.getString(R.string.trash_deleted_on),
                    formatTrashDate(msg.deletedAt)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onRestore) {
            Icon(
                Icons.Rounded.RestoreFromTrash,
                contentDescription = stringResource(R.string.access_restore),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDeleteForever) {
            Icon(
                Icons.Rounded.DeleteForever,
                contentDescription = stringResource(R.string.access_delete_forever),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatTrashDate(ts: Long): String =
    if (ts <= 0) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))

@Composable
private fun TrashReasonTag() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = stringResource(R.string.trash_reason_blocked),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
