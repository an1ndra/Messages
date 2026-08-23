package com.anindra.messages.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.data.SettingsStore
import com.anindra.messages.data.SoundStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenTrash: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current

    var themeDialog by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(vm.settings.notificationsEnabled) }
    var sounds by remember { mutableStateOf(vm.settings.soundsEnabled) }
    var delivery by remember { mutableStateOf(vm.settings.deliveryReportsEnabled) }
    val themeMode = vm.themeMode

    var pinned by remember { mutableStateOf(vm.settings.pinnedEnabled) }
    var archiving by remember { mutableStateOf(vm.settings.archivingEnabled) }
    var drafts by remember { mutableStateOf(vm.settings.draftsEnabled) }
    var swipeActions by remember { mutableStateOf(vm.settings.swipeActionsEnabled) }
    var blocking by remember { mutableStateOf(vm.settings.blockingEnabled) }
    var forwarding by remember { mutableStateOf(vm.settings.forwardingEnabled) }
    var unreadAtTop by remember { mutableStateOf(vm.settings.unreadAtTopEnabled) }
    var scheduledMessages by remember { mutableStateOf(vm.settings.scheduledMessagesEnabled) }
    var delayedSending by remember { mutableStateOf(vm.settings.delayedSendingEnabled) }
    var highlightLinks by remember { mutableStateOf(vm.settings.highlightLinks) }
    var privacyMode by remember { mutableStateOf(vm.settings.privacyModeEnabled) }
    var delaySeconds by remember { mutableIntStateOf(vm.settings.delaySeconds) }

    var simDialog by remember { mutableStateOf(false) }
    var sims by remember { mutableStateOf(emptyList<SubscriptionInfo>()) }
    var backingUp by remember { mutableStateOf(false) }
    var delayDialog by remember { mutableStateOf(false) }

    val simPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sims = try {
                val sm = context.getSystemService(SubscriptionManager::class.java)
                sm?.activeSubscriptionInfoList ?: emptyList()
            } catch (_: Exception) { emptyList() }
            simDialog = true
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            vm.importDatabase(it) { ok ->
                Toast.makeText(
                    context,
                    if (ok) "Backup restored. Restart app to apply." else "Import failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    var incomingSoundKey by remember { mutableStateOf(vm.settings.incomingSound) }
    var outgoingSoundKey by remember { mutableStateOf(vm.settings.outgoingSound) }
    var incomingSoundLabel by remember { mutableStateOf(vm.settings.incomingSoundLabel) }
    var outgoingSoundLabel by remember { mutableStateOf(vm.settings.outgoingSoundLabel) }
    var soundPickerOpen by remember { mutableStateOf<String?>(null) }
    var soundImportTarget by remember { mutableStateOf<String?>(null) }

    val soundImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = soundImportTarget
        if (uri != null && target != null) {
            val key = SoundStore.importCustom(context, uri)
            if (key != null) {
                val label = SoundStore.entryTitle(context, key, "")
                if (target == "incoming") {
                    vm.settings.incomingSound = key
                    vm.settings.incomingSoundLabel = label
                    incomingSoundKey = key
                    incomingSoundLabel = label
                } else {
                    vm.settings.outgoingSound = key
                    vm.settings.outgoingSoundLabel = label
                    outgoingSoundKey = key
                    outgoingSoundLabel = label
                }
                SoundStore.play(context, key)
            } else {
                Toast.makeText(context, "Couldn't import that audio file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun selectSound(target: String, key: String?, label: String?) {
        if (key == null) {
            soundImportTarget = target
            soundImportLauncher.launch(arrayOf("audio/*", "application/ogg"))
            return
        }
        if (target == "incoming") {
            vm.settings.incomingSound = key
            vm.settings.incomingSoundLabel = label.orEmpty()
            incomingSoundKey = key
            incomingSoundLabel = label.orEmpty()
        } else {
            vm.settings.outgoingSound = key
            vm.settings.outgoingSoundLabel = label.orEmpty()
            outgoingSoundKey = key
            outgoingSoundLabel = label.orEmpty()
        }
        SoundStore.play(context, key)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("General settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = "Notifications",
                    subtitle = "Show message notifications",
                    checked = notifications,
                    onChecked = { notifications = it; vm.settings.notificationsEnabled = it }
                )
                SettingsRow(
                    title = "Message sounds",
                    subtitle = "Hear outgoing and incoming message sounds",
                    checked = sounds,
                    onChecked = { sounds = it; vm.settings.soundsEnabled = it }
                )
                SettingsRow(
                    title = "Delivery reports",
                    subtitle = "Find out when an SMS message is delivered",
                    checked = delivery,
                    onChecked = { delivery = it; vm.settings.deliveryReportsEnabled = it }
                )
                SettingsRow(
                    title = "Mark all as read",
                    subtitle = "Clear unread badges for every conversation",
                    onClick = {
                        vm.markAllRead()
                        Toast.makeText(context, "All conversations marked as read", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = "Incoming message sound",
                    subtitle = soundSubtitle(incomingSoundKey, incomingSoundLabel),
                    onClick = { soundPickerOpen = "incoming" }
                )
                SettingsRow(
                    title = "Sent message sound",
                    subtitle = soundSubtitle(outgoingSoundKey, outgoingSoundLabel),
                    onClick = { soundPickerOpen = "outgoing" }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = "Theme",
                    subtitle = themeLabel(themeMode),
                    onClick = { themeDialog = true }
                )
                val currentSimLabel = if (vm.settings.simSubscriptionId == -1) "Default"
                else sims.firstOrNull { it.subscriptionId == vm.settings.simSubscriptionId }?.displayName?.toString()
                    ?: "SIM ${vm.settings.simSubscriptionId}"
                SettingsRow(
                    title = "SIM card",
                    subtitle = currentSimLabel,
                    onClick = {
                        val hasPerm = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                                PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            sims = try {
                                val sm = context.getSystemService(SubscriptionManager::class.java)
                                sm?.activeSubscriptionInfoList ?: emptyList()
                            } catch (_: Exception) { emptyList() }
                            simDialog = true
                        } else {
                            simPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                        }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = "Drafts",
                    subtitle = "Auto-save unsent text as drafts",
                    checked = drafts,
                    onChecked = { drafts = it; vm.settings.draftsEnabled = it }
                )
                SettingsRow(
                    title = "Archiving",
                    subtitle = "Allow archiving conversations",
                    checked = archiving,
                    onChecked = { archiving = it; vm.settings.archivingEnabled = it }
                )
                SettingsRow(
                    title = "Pinned conversations",
                    subtitle = "Show pinned conversations at top",
                    checked = pinned,
                    onChecked = { pinned = it; vm.settings.pinnedEnabled = it }
                )
                SettingsRow(
                    title = "Swipe actions",
                    subtitle = "Enable swipe to archive/delete",
                    checked = swipeActions,
                    onChecked = { swipeActions = it; vm.settings.swipeActionsEnabled = it }
                )
                SettingsRow(
                    title = "Unread at top",
                    subtitle = "Sort unread messages at top",
                    checked = unreadAtTop,
                    onChecked = { unreadAtTop = it; vm.settings.unreadAtTopEnabled = it }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = "Forwarding",
                    subtitle = "Enable message forwarding",
                    checked = forwarding,
                    onChecked = { forwarding = it; vm.settings.forwardingEnabled = it }
                )
                SettingsRow(
                    title = "Highlight links",
                    subtitle = "Tap links in messages to open the website",
                    checked = highlightLinks,
                    onChecked = { highlightLinks = it; vm.settings.highlightLinks = it }
                )
                SettingsRow(
                    title = "Scheduled messages",
                    subtitle = "Enable scheduling messages",
                    checked = scheduledMessages,
                    onChecked = { scheduledMessages = it; vm.settings.scheduledMessagesEnabled = it }
                )
                SettingsRow(
                    title = "Delayed sending",
                    subtitle = "Wait before sending a message",
                    checked = delayedSending,
                    onChecked = {
                        delayedSending = it; vm.settings.delayedSendingEnabled = it
                        if (it) delayDialog = true
                    }
                )
                if (delayedSending) {
                    SettingsRow(
                        title = "Delay seconds",
                        subtitle = "${delaySeconds}s before sending",
                        onClick = { delayDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = "Number blocking",
                    subtitle = "Block numbers from messaging you",
                    checked = blocking,
                    onChecked = { blocking = it; vm.settings.blockingEnabled = it }
                )
                SettingsRow(
                    title = "Privacy mode",
                    subtitle = "Hide content from screenshots and screen recording",
                    checked = privacyMode,
                    onChecked = {
                        privacyMode = it
                        (context as? Activity)?.let { act -> vm.setPrivacyMode(act, it) }
                    }
                )
                SettingsRow(
                    title = "Trash",
                    subtitle = "Deleted conversations · purged after 30 days",
                    onClick = onOpenTrash
                )
                SettingsRow(
                    title = "Backup messages",
                    subtitle = if (backingUp) "Saving..." else "Save database to Documents/Messages",
                    onClick = {
                        backingUp = true
                        vm.backupDatabase { ok ->
                            backingUp = false
                            Toast.makeText(
                                context,
                                if (ok) "Backup saved to Documents/Messages" else "Backup failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                SettingsRow(
                    title = "Import messages",
                    subtitle = "Import a backup database file",
                    onClick = {
                        importLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3"))
                    }
                )
            }

            val scheduledMsgs by vm.scheduledMessages().collectAsState(initial = emptyList())
            if (scheduledMsgs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    scheduledMsgs.forEach { sm ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Schedule, null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sm.body, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    "To: ${sm.address} · ${fmt.format(Date(sm.timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.cancelScheduledMessage(sm.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Cancel, "Cancel", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Messages 1.0 — offline SMS clone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(bottom = 16.dp)
            )
        }
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Choose theme") },
            text = {
                Column {
                    listOf(
                        "light" to "Light",
                        "dark" to "Dark",
                        "system" to "System default"
                    ).forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.setTheme(value) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = themeMode == value,
                                onClick = { vm.setTheme(value) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { themeDialog = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { themeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (simDialog) {
        var selected by remember { mutableIntStateOf(vm.settings.simSubscriptionId) }
        val options = mutableListOf(-1 to "Default (System)")
        sims.forEach { sub ->
            val carrier = sub.carrierName?.toString()?.ifBlank { null }
            val label = if (carrier != null) "$carrier (SIM ${sub.simSlotIndex + 1})" else "SIM ${sub.simSlotIndex + 1}"
            options += sub.subscriptionId to label
        }
        AlertDialog(
            onDismissRequest = { simDialog = false },
            title = { Text("SIM card") },
            text = {
                Column {
                    options.forEach { (id, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = id }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selected == id,
                                onClick = { selected = id }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.settings.simSubscriptionId = selected
                    simDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { simDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (delayDialog) {
        val options = listOf(1, 3, 5, 10, 30)
        AlertDialog(
            onDismissRequest = { delayDialog = false },
            title = { Text("Delay before sending") },
            text = {
                Column {
                    Text(
                        "Choose how long to wait before sending:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    options.forEach { secs ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    delaySeconds = secs
                                    vm.settings.delaySeconds = secs
                                    delayDialog = false
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = delaySeconds == secs,
                                onClick = {
                                    delaySeconds = secs
                                    vm.settings.delaySeconds = secs
                                    delayDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${secs}s")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { delayDialog = false }) { Text("Cancel") }
            }
        )
    }

    soundPickerOpen?.let { target ->
        val currentKey = if (target == "incoming") incomingSoundKey else outgoingSoundKey
        SoundPickerDialog(
            context = context,
            currentKey = currentKey,
            onSelect = { key, label -> selectSound(target, key, label) },
            onDismiss = { soundPickerOpen = null }
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

private fun themeLabel(mode: String) = when (mode) {
    "light" -> "Light"
    "dark" -> "Dark"
    else -> "System default"
}

private fun soundSubtitle(key: String, label: String): String = when {
    key == SettingsStore.SOUND_DEFAULT -> "Default (system tone)"
    key == SettingsStore.SOUND_SILENT -> "Silent"
    else -> label.ifEmpty { "Custom sound" }
}

@Composable
private fun SoundPickerDialog(
    context: android.content.Context,
    currentKey: String,
    onSelect: (String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val systemSounds = remember {
        runCatching { SoundStore.listSystemSounds(context) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose sound") },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                item {
                    SoundOption(
                        selected = currentKey == SettingsStore.SOUND_DEFAULT,
                        title = "App default (beep)",
                        icon = Icons.Rounded.Notifications,
                        onClick = { onSelect(SettingsStore.SOUND_DEFAULT, null) }
                    )
                }
                item {
                    SoundOption(
                        selected = currentKey == SettingsStore.SOUND_SILENT,
                        title = "Silent",
                        icon = Icons.Rounded.Cancel,
                        onClick = { onSelect(SettingsStore.SOUND_SILENT, null) }
                    )
                }
                items(systemSounds) { sound ->
                    SoundOption(
                        selected = currentKey == sound.key,
                        title = sound.title,
                        icon = Icons.Rounded.VolumeUp,
                        onClick = { onSelect(sound.key, sound.title) }
                    )
                }
                item {
                    SoundOption(
                        selected = currentKey.startsWith("custom:"),
                        title = if (currentKey.startsWith("custom:"))
                            SoundStore.entryTitle(context, currentKey, "Custom sound")
                        else "Choose from storage...",
                        icon = Icons.Rounded.FolderOpen,
                        onClick = { onSelect(null, null) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun SoundOption(
    selected: Boolean,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    checked: Boolean? = null,
    onChecked: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(enabled = onClick != null || checked != null) {
                if (checked != null && onChecked != null) onChecked(!checked) else onClick?.invoke()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (checked != null && onChecked != null) {
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = { onChecked(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}
