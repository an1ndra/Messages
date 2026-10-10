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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.R
import com.anindra.messages.data.ScheduledMessage
import com.anindra.messages.data.ScheduledTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Every scheduled message across all conversations, searchable by contact.
 * Rows are the same card the chat shows, so a schedule can be inspected and
 * moved from here without hunting for the conversation it belongs to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenChat: (conversationId: Long) -> Unit
) {
    val context = LocalContext.current
    val scheduled by vm.scheduledMessages().collectAsState(initial = emptyList())
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ScheduledMessage?>(null) }

    val contacts by vm.contacts.collectAsState()

    fun labelFor(sm: ScheduledMessage): String {
        val key = phoneKey(sm.address)
        val name = contacts.firstOrNull { phoneKey(it.number) == key }?.name
        return name?.takeIf { it.isNotBlank() } ?: formatPhoneNumber(sm.address)
    }

    val visible = remember(scheduled, query, contacts) {
        val q = query.trim()
        if (q.isEmpty()) scheduled
        else scheduled.filter { labelFor(it).contains(q, ignoreCase = true) }
    }

    BackHandler(enabled = searching) { searching = false; query = "" }
    BackHandler(enabled = !searching, onBack = onBack)

    // This screen builds its header by hand and so has no Surface to derive a
    // content colour from. Left unset, LocalContentColor is Color.Black, which
    // renders the title, back arrow and search icon invisible in dark mode.
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface
    ) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // same header shape as the conversation list: the title is replaced in
        // place by the field, so the screen never grows a second row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (searching) {
                IconButton(onClick = { searching = false; query = "" }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        stringResource(R.string.icon_close_search)
                    )
                }
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.settings_scheduled_search)) },
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
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        stringResource(R.string.icon_back)
                    )
                }
                Text(
                    stringResource(R.string.settings_scheduled_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { searching = true }) {
                    Icon(Icons.Outlined.Search, stringResource(R.string.icon_search))
                }
            }
        }

        Column(Modifier.weight(1f)) {
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(
                            if (scheduled.isEmpty()) R.string.settings_scheduled_none
                            else R.string.settings_scheduled_no_match
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = SettingsLayout.ROW_CONTENT_PADDING,
                        vertical = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(visible, key = { it.id }) { sm ->
                        ScheduledRow(
                            address = sm.address,
                            label = labelFor(sm),
                            body = sm.body,
                            timestamp = sm.timestamp,
                            onOpenChat = { onOpenChat(sm.conversationId) },
                            onReschedule = { editing = sm },
                            onCancel = { vm.cancelScheduledMessage(sm.id) }
                        )
                    }
                }
            }
        }
    }
    }

    editing?.let { sm ->
        ReschedulePicker(
            timestamp = sm.timestamp,
            onDone = { millis ->
                vm.rescheduleMessage(sm.id, millis)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
internal fun ScheduledRow(
    address: String,
    label: String,
    body: String,
    timestamp: Long,
    onOpenChat: () -> Unit,
    onReschedule: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val is24Hour = is24HourFormat(context)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(SettingsLayout.OUTER_RADIUS),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            PersonAvatar(address)
            Spacer(Modifier.width(16.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onOpenChat() }
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    context.getString(R.string.chat_scheduled_prefix) +
                        formatDateTime(timestamp, "MMM d,", is24Hour),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onReschedule,
                modifier = Modifier.size(A11y.touchTarget(32.dp))
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    stringResource(R.string.settings_scheduled_reschedule),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(A11y.touchTarget(32.dp))
            ) {
                Icon(
                    Icons.Rounded.Close,
                    stringResource(R.string.icon_cancel),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Date + time sheets that re-arm an existing schedule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReschedulePicker(timestamp: Long, onDone: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(timestamp).atZone(zone)
    var step by remember { mutableStateOf("date") }
    var pickedDate by remember { mutableStateOf<java.time.LocalDate?>(null) }

    if (step == "date") {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = ScheduledTime.selectedDate(
                        datePickerState.selectedDateMillis ?: current.toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    )
                    step = "time"
                }) { Text(stringResource(R.string.chat_schedule)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        val timePickerState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.chat_select_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val day = pickedDate ?: current.toLocalDate()
                    onDone(
                        day.atTime(timePickerState.hour, timePickerState.minute)
                            .atZone(zone).toInstant().toEpochMilli()
                    )
                }) { Text(stringResource(R.string.chat_schedule)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
            }
        )
    }
}