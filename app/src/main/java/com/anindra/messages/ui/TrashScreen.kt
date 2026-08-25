package com.anindra.messages.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.data.Conversation
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val trashed by vm.trashedConversations().collectAsState(initial = emptyList())
    val context = LocalContext.current

    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showDeleteForeverDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (trashed.isNotEmpty()) {
                        TextButton(onClick = { showEmptyTrashDialog = true }) {
                            Text("Empty trash", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (trashed.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                    "Trash is empty",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Deleted conversations are permanently removed after 30 days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                items(trashed, key = { it.id }) { convo ->
                    TrashRow(
                        convo = convo,
                        onRestore = { vm.restoreFromTrash(convo.id) },
                        onDeleteForever = {
                            deleteTarget = convo.id
                            showDeleteForeverDialog = true
                        }
                    )
                }
            }
        }

        if (showEmptyTrashDialog) {
            AlertDialog(
                onDismissRequest = { showEmptyTrashDialog = false },
                title = { Text("Empty trash") },
                text = {
                    Text(
                        "Delete all ${trashed.size} conversations from trash? This can't be undone."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.emptyTrash()
                        showEmptyTrashDialog = false
                        Toast.makeText(context, "Trash emptied", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Empty trash", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyTrashDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteForeverDialog && deleteTarget != null) {
            val targetConvo = trashed.find { it.id == deleteTarget }
            AlertDialog(
                onDismissRequest = {
                    showDeleteForeverDialog = false
                    deleteTarget = null
                },
                title = { Text("Delete conversation") },
                text = {
                    val name = targetConvo?.name?.ifBlank { targetConvo?.address ?: "conversation" }
                    Text(
                        "Permanently delete '\u201C$name\u201D'? This can't be undone."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget?.let { vm.deleteForever(it) }
                        showDeleteForeverDialog = false
                        deleteTarget = null
                        Toast.makeText(context, "Conversation deleted", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteForeverDialog = false
                        deleteTarget = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun TrashRow(
    convo: Conversation,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Column {
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
                    text = convo.name.ifBlank { convo.address },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Deleted ${formatTrashDate(convo.deletedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Rounded.RestoreFromTrash,
                    contentDescription = "Restore",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteForever) {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = "Delete forever",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatTrashDate(ts: Long): String =
    if (ts <= 0) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))
