package com.anindra.messages.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.R

/**
 * Picks several people at once to add to a conversation, turning it into a
 * group. People already on the conversation are listed but not selectable, so
 * the same person cannot be added twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPeopleScreen(
    vm: AppViewModel,
    conversationId: Long,
    onBack: () -> Unit,
    onDone: (addresses: List<String>) -> Unit
) {
    BackHandler(onBack = onBack)
    val contacts by vm.contacts.collectAsState()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    var existing by remember { mutableStateOf(emptySet<String>()) }

    androidx.compose.runtime.LaunchedEffect(conversationId) {
        existing = vm.conversationRecipients(conversationId).toSet()
    }

    val filtered = contacts.filter {
        (it.name.contains(query, ignoreCase = true) || it.number.contains(query)) &&
            it.number !in existing
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        // Same control the conversation list uses: a bare field
                        // with no underline or container, so the two read alike.
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    } else {
                        Text(stringResource(R.string.contact_add_people))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (searching) {
                                searching = false
                                query = ""
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            if (searching) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                            stringResource(if (searching) R.string.icon_close_search else R.string.common_close),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.icon_search))
                        }
                    }
                    TextButton(
                        enabled = chosen.isNotEmpty(),
                        onClick = { onDone(chosen.toList()) }
                    ) { Text(stringResource(R.string.contact_create_group)) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.number }) { contact ->
                    val picked = contact.number in chosen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chosen = if (picked) chosen - contact.number
                                else chosen + contact.number
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Same avatar the conversation list uses, so people look
                        // identical to how they appear on the home page.
                        PersonAvatar(contact.number)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                contact.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                BidiText.ltr(contact.number),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (contact.workProfile) {
                            Spacer(Modifier.width(4.dp))
                            WorkProfileBadge()
                        }
                        Spacer(Modifier.width(12.dp))
                        // Trailing, so the avatar stays on the leading edge.
                        Box(
                            Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (picked) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = stringResource(R.string.contact_selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
