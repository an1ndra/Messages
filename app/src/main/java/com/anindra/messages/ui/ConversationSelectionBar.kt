package com.anindra.messages.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anindra.messages.R

/**
 * The contextual bar that replaces the inbox title while conversations are
 * selected (#340). Shared by the redesigned and the legacy conversation lists,
 * so the two surfaces cannot drift apart.
 */
@Composable
fun ConversationSelectionBar(
    selectedCount: Int,
    archivingEnabled: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onSelectAll: () -> Unit,
    onOptions: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.access_clear_selection)
                )
            }
            Text(
                text = androidx.compose.ui.platform.LocalContext.current
                    .getString(R.string.contact_people_selected, selectedCount),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.sheet_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            if (archivingEnabled) {
                IconButton(onClick = onArchive) {
                    Icon(
                        Icons.Rounded.Archive,
                        contentDescription = stringResource(R.string.access_archive)
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.icon_more_options)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_select_all)) },
                        onClick = { menuOpen = false; onSelectAll() }
                    )
                    // The per-conversation options only make sense for one
                    // thread, so they stay behind the same long-press that
                    // started the selection.
                    if (onOptions != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.access_conversation_options)) },
                            onClick = { menuOpen = false; onOptions() }
                        )
                    }
                }
            }
        }
    }
}
