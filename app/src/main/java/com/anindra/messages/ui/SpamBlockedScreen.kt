package com.anindra.messages.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.R
import com.anindra.messages.data.BlockedMessage
import com.anindra.messages.data.Conversation
import kotlinx.coroutines.launch

/** Google-Messages-style "Spam & blocked" folder. Two tabs: blocked
 *  conversations (blocked numbers) and blocked messages (keyword blocks). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamBlockedScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val conversations by vm.conversations.collectAsState(initial = emptyList())
    val blockedConversations = remember(conversations) { conversations.filter { it.blocked } }
    val blockedMessages by vm.blockedMessages().collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pendingDelete = remember { mutableStateListOf<Long>() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conversations_spam_blocked)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.icon_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ExpressiveTabs(
                tabs = listOf(
                    ExpressiveTab(R.string.spam_tab_conversations),
                    ExpressiveTab(R.string.spam_tab_messages)
                ),
                selected = tab,
                onSelect = { tab = it }
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab == 0) {
                    ConversationsTab(
                        blocked = blockedConversations,
                        onOpenConversation = onOpenConversation,
                        onUnblock = { convo ->
                            vm.unblockNumber(convo.address)
                            Toast.makeText(context, context.getString(R.string.chat_number_unblocked), Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    MessagesTab(
                        messages = blockedMessages.filter { it.id !in pendingDelete },
                        onDelete = { msg ->
                            pendingDelete.add(msg.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.spam_message_deleted),
                                    actionLabel = context.getString(R.string.action_undo),
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    pendingDelete.remove(msg.id)
                                } else {
                                    vm.deleteBlockedMessage(msg.id)
                                    pendingDelete.remove(msg.id)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationsTab(
    blocked: List<Conversation>,
    onOpenConversation: (Long) -> Unit,
    onUnblock: (Conversation) -> Unit
) {
    if (blocked.isEmpty()) {
        EmptyFolder(Icons.Rounded.Block, stringResource(R.string.conversations_spam_blocked_empty))
        return
    }
    val context = LocalContext.current
    val now = LocalNowTick.current
    LazyColumn(Modifier.fillMaxSize()) {
        items(blocked, key = { it.id }) { convo ->
            val sender = if (convo.name == convo.address) BidiText.ltr(convo.display) else convo.name
            val blockedLabel = stringResource(R.string.access_blocked)
            Row(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = A11y.describe(
                            sender, blockedLabel, formatListTime(convo.timestamp, now, context)
                        )
                        role = Role.Button
                        onClick(label = context.getString(R.string.access_open_conversation)) {
                            onOpenConversation(convo.id)
                            true
                        }
                    }
                    .clickable { onOpenConversation(convo.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PersonAvatar(convo.address)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = sender,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatListTime(convo.timestamp, now, context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                TextButton(onClick = { onUnblock(convo) }) {
                    Text(stringResource(R.string.chat_unblock))
                }
            }
        }
    }
}

@Composable
private fun MessagesTab(
    messages: List<BlockedMessage>,
    onDelete: (BlockedMessage) -> Unit
) {
    if (messages.isEmpty()) {
        EmptyFolder(Icons.Outlined.DeleteOutline, stringResource(R.string.spam_blocked_messages_empty))
        return
    }
    val context = LocalContext.current
    val now = LocalNowTick.current
    LazyColumn(Modifier.fillMaxSize()) {
        items(messages, key = { it.id }) { msg ->
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
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = A11y.describe(sender, msg.body)
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatListTime(msg.timestamp, now, context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = { onDelete(msg) }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFolder(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
