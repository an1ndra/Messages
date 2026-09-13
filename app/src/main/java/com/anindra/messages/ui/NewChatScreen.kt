package com.anindra.messages.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anindra.messages.R

data class Contact(val name: String, val number: String, val workProfile: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(vm: com.anindra.messages.AppViewModel, onBack: () -> Unit, onPick: (String, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.activity.compose.BackHandler(onBack = onBack)
    val contacts by vm.contacts.collectAsState()
    val filtered = contacts.filter {
        it.name.contains(query, ignoreCase = true) || it.number.contains(query)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.new_chat_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.new_chat_hint)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider()

            // Manual entry option
            val canSendToQuery = isPhoneNumber(query.trim())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canSendToQuery) {
                        onPick(query.trim(), query.trim())
                    }
                    .padding(16.dp)
            ) {
                PersonAvatar("#", size = 40.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(context.getString(R.string.new_chat_send_to, query.ifBlank { "number" }),
                        fontWeight = FontWeight.Medium,
                        color = if (query.isNotBlank() && !canSendToQuery)
                            MaterialTheme.colorScheme.error else Color.Unspecified)
                    if (query.isNotBlank() && !canSendToQuery) {
                        Text(stringResource(R.string.new_chat_only_phone),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider()

            LazyColumn {
                items(filtered, key = { it.number }) { contact ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(contact.number, contact.name) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        PersonAvatar(contact.number, size = 40.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(contact.name, fontWeight = FontWeight.Medium)
                                if (contact.workProfile) {
                                    Spacer(Modifier.width(6.dp))
                                    WorkProfileBadge()
                                }
                            }
                            Text(contact.number,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
