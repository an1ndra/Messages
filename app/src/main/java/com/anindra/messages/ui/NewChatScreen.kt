package com.anindra.messages.ui

import android.provider.ContactsContract
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(val name: String, val number: String)

@Composable
fun rememberContacts(): List<Contact> {
    val context = LocalContext.current
    return produceState(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            val out = mutableListOf<Contact>()
            try {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )?.use { c ->
                    val seen = mutableSetOf<String>()
                    while (c.moveToNext()) {
                        val name = c.getString(0) ?: continue
                        val num = c.getString(1) ?: continue
                        if (seen.add(num.filter { it.isDigit() })) out.add(Contact(name, num))
                    }
                }
            } catch (_: SecurityException) {
            }
            out
        }
    }.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(onBack: () -> Unit, onPick: (String, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    androidx.activity.compose.BackHandler(onBack = onBack)
    val contacts = rememberContacts().filter {
        it.name.contains(query, ignoreCase = true) || it.number.contains(query)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("New conversation") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Enter name or phone number") },
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
                    Text("Send to \u201C${query.ifBlank { "number" }}\u201D",
                        fontWeight = FontWeight.Medium,
                        color = if (query.isNotBlank() && !canSendToQuery)
                            MaterialTheme.colorScheme.error else Color.Unspecified)
                    if (query.isNotBlank() && !canSendToQuery) {
                        Text("Only phone numbers can be messaged",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider()

            LazyColumn {
                items(contacts) { contact ->
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
                            Text(contact.name, fontWeight = FontWeight.Medium)
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
