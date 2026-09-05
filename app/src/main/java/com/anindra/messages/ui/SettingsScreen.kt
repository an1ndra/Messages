package com.anindra.messages.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.data.SettingsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PinDialogMode { SET, ENTER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenTrash: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current

    val revision by vm.settings.revision.collectAsState()

    var themeDialog by remember { mutableStateOf(false) }
    var notifications by remember(revision) { mutableStateOf(vm.settings.notificationsEnabled) }
    var delivery by remember(revision) { mutableStateOf(vm.settings.deliveryReportsEnabled) }
    var sendSound by remember(revision) { mutableStateOf(vm.settings.sendSoundEnabled) }
    var receiveSound by remember(revision) { mutableStateOf(vm.settings.receiveSoundEnabled) }
    var showSim by remember(revision) { mutableStateOf(vm.settings.showSimIndicator) }
    val themeMode = vm.themeMode

    var pinned by remember(revision) { mutableStateOf(vm.settings.pinnedEnabled) }
    var archiving by remember(revision) { mutableStateOf(vm.settings.archivingEnabled) }
    var drafts by remember(revision) { mutableStateOf(vm.settings.draftsEnabled) }
    var swipeActions by remember(revision) { mutableStateOf(vm.settings.swipeActionsEnabled) }
    var blocking by remember(revision) { mutableStateOf(vm.settings.blockingEnabled) }
    var forwarding by remember(revision) { mutableStateOf(vm.settings.forwardingEnabled) }
    var unreadAtTop by remember(revision) { mutableStateOf(vm.settings.unreadAtTopEnabled) }
    var scheduledMessages by remember(revision) { mutableStateOf(vm.settings.scheduledMessagesEnabled) }
    var delayedSending by remember(revision) { mutableStateOf(vm.settings.delayedSendingEnabled) }
    var highlightLinks by remember(revision) { mutableStateOf(vm.settings.highlightLinks) }
    var privacyMode by remember(revision) { mutableStateOf(vm.settings.privacyModeEnabled) }
    var appLock by remember(revision) { mutableStateOf(vm.settings.appLockEnabled) }
    var delaySeconds by remember(revision) { mutableIntStateOf(vm.settings.delaySeconds) }

    var simDialog by remember { mutableStateOf(false) }
    var sims by remember { mutableStateOf(emptyList<SubscriptionInfo>()) }
    var backingUp by remember { mutableStateOf(false) }
    var delayDialog by remember { mutableStateOf(false) }

    var pinMode by remember { mutableStateOf<PinDialogMode?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportMode by remember {
        mutableStateOf(com.anindra.messages.data.ImportMode.MERGE)
    }
    var pendingImportFormat by remember {
        mutableStateOf(com.anindra.messages.data.BackupFormat.LEGACY)
    }
    var importModeDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    val simPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sims = try {
                val sm = context.getSystemService(SubscriptionManager::class.java)
                sm?.activeSubscriptionInfoList ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            simDialog = true
        }
    }

    val showImportResult: (com.anindra.messages.data.Repository.ImportResult) -> Unit = { result ->
        val msg = when (result) {
            is com.anindra.messages.data.Repository.ImportResult.Success ->
                if (result.merged != null) "Backup merged (${result.merged} messages). Existing conversations kept."
                else "Backup restored. Restart app to apply."
            is com.anindra.messages.data.Repository.ImportResult.Error ->
                "Import failed: ${result.message}"
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            vm.peekBackupFormat(it) { format ->
                pendingImportUri = it
                pendingImportFormat = format
                importModeDialog = true
            }
        }
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
                    title = "Notification sounds",
                    subtitle = "Show message notifications",
                    checked = notifications,
                    onChecked = { notifications = it; vm.settings.notificationsEnabled = it }
                )
                SettingsRow(
                    title = "Send sound",
                    subtitle = "Play sound when sending a message",
                    checked = sendSound,
                    onChecked = { sendSound = it; vm.settings.sendSoundEnabled = it }
                )
                SettingsRow(
                    title = "Receive sound",
                    subtitle = "Play sound when receiving a message",
                    checked = receiveSound,
                    onChecked = { receiveSound = it; vm.settings.receiveSoundEnabled = it }
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
                            } catch (_: Exception) {
                                emptyList()
                            }
                            simDialog = true
                        } else {
                            simPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                        }
                    }
                )
                SettingsRow(
                    title = "SIM indicator",
                    subtitle = "Show which SIM was used for each message",
                    checked = showSim,
                    onChecked = { showSim = it; vm.settings.showSimIndicator = it }
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
                    title = "App lock",
                    subtitle = "Require fingerprint or PIN to open app",
                    checked = appLock,
                    onChecked = { enable ->
                        val canAuth = androidx.biometric.BiometricManager.from(context)
                            .canAuthenticate(
                                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            )
                        if (enable && canAuth != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                            Toast.makeText(
                                context,
                                "Set up a screen lock (fingerprint, face, or PIN) on your device first",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            appLock = enable
                            vm.settings.appLockEnabled = enable
                        }
                    }
                )
                SettingsRow(
                    title = "Trash",
                    subtitle = "Deleted conversations · purged after 30 days",
                    onClick = onOpenTrash
                )
                SettingsRow(
                    title = "Backup messages",
                    subtitle = if (backingUp) "Saving..." else "PIN-protected save to Documents/Messages",
                    onClick = {
                        pinMode = PinDialogMode.SET
                        pinInput = ""
                        pinConfirm = ""
                        pinError = null
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
                            IconButton(
                                onClick = { vm.cancelScheduledMessage(sm.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.Cancel, "Cancel", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Messages 1.0 — offline SMS",
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

    if (importModeDialog) {
        val uri = pendingImportUri
        AlertDialog(
            onDismissRequest = {
                importModeDialog = false
                pendingImportUri = null
            },
            title = { Text("Import backup") },
            text = {
                Column {
                    Text(
                        "How should this backup be applied to your current messages?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ImportChoiceRow(
                        title = "Merge with existing messages",
                        subtitle = "Keep current messages and add the backup's",
                        onClick = {
                            importModeDialog = false
                            pendingImportMode = com.anindra.messages.data.ImportMode.MERGE
                            if (pendingImportFormat == com.anindra.messages.data.BackupFormat.PIN) {
                                pinInput = ""
                                pinError = null
                                pinMode = PinDialogMode.ENTER
                            } else if (uri != null) {
                                vm.importDatabase(uri, null, pendingImportMode, showImportResult)
                            }
                        }
                    )
                    ImportChoiceRow(
                        title = "Restore (replace all)",
                        subtitle = "Remove current messages and restore the backup",
                        onClick = {
                            importModeDialog = false
                            pendingImportMode = com.anindra.messages.data.ImportMode.REPLACE
                            if (pendingImportFormat == com.anindra.messages.data.BackupFormat.PIN) {
                                pinInput = ""
                                pinError = null
                                pinMode = PinDialogMode.ENTER
                            } else if (uri != null) {
                                vm.importDatabase(uri, null, pendingImportMode, showImportResult)
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    importModeDialog = false
                    pendingImportUri = null
                }) { Text("Cancel") }
            }
        )
    }

    pinMode?.let { mode ->
        AlertDialog(
            onDismissRequest = {
                pinMode = null
                pendingImportUri = null
            },
            title = {
                Text(
                    if (mode == PinDialogMode.SET) "Set backup PIN"
                    else "Enter backup PIN"
                )
            },
            text = {
                Column {
                    if (mode == PinDialogMode.SET) {
                        Text(
                            "Your backup is protected by this PIN. You'll need it to " +
                                "restore — even after reinstalling the app or on a new phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { new ->
                            if (new.length <= 16 && new.all { it.isDigit() }) pinInput = new
                        },
                        label = { Text("4-16 digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mode == PinDialogMode.SET) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pinConfirm,
                            onValueChange = { new ->
                                if (new.length <= 16 && new.all { it.isDigit() }) pinConfirm = new
                            },
                            label = { Text("Repeat PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (pinError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = pinError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pin = pinInput.trim()
                    when {
                        pin.length < 4 -> pinError = "PIN must be at least 4 digits"
                        mode == PinDialogMode.SET && pin != pinConfirm ->
                            pinError = "PINs do not match"
                        else -> {
                            if (mode == PinDialogMode.SET) {
                                pinMode = null
                                backingUp = true
                                vm.backupDatabase(pin) { ok ->
                                    backingUp = false
                                    Toast.makeText(
                                        context,
                                        if (ok) "Backup saved with PIN to Documents/Messages"
                                        else "Backup failed",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                val uri = pendingImportUri
                                if (uri != null) {
                                    pinMode = null
                                    pendingImportUri = null
                                    vm.importDatabase(uri, pin, pendingImportMode, showImportResult)
                                }
                            }
                        }
                    }
                }) { Text(if (mode == PinDialogMode.SET) "Save" else "Import") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pinMode = null
                    pendingImportUri = null
                }) { Text("Cancel") }
            }
        )
    }

    val importLoading = vm.importLoading.value
    if (importLoading != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Loading messages") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("$importLoading messages loaded", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {},
            dismissButton = {}
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

@Composable
private fun ImportChoiceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        RadioButton(selected = false, onClick = onClick)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
