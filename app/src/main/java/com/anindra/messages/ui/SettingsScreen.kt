package com.anindra.messages.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.data.SettingsStore
import com.anindra.messages.data.SimCard
import com.anindra.messages.data.SimCards
import com.anindra.messages.data.SimSelection
import com.anindra.messages.data.SimLabel
import com.anindra.messages.data.SimLabels
import androidx.compose.ui.res.stringResource
import com.anindra.messages.R
import com.anindra.messages.sms.NotificationHelper
import com.anindra.messages.ui.theme.LocalLargeTouchTargets

private enum class PinDialogMode { SET, ENTER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenTrash: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
    onOpenSpamBlocked: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState()
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current

    val revision by vm.settings.revision.collectAsState()

    var themeDialog by remember { mutableStateOf(false) }
    var notifications by remember(revision) { mutableStateOf(vm.settings.notificationsEnabled) }
    var delivery by remember(revision) { mutableStateOf(vm.settings.deliveryReportsEnabled) }
    var notificationSound by remember(revision) { mutableStateOf(vm.settings.notificationSound) }
    val notificationSoundOptions = listOf(
        SettingsStore.NOTIFY_SOUND_DEFAULT to context.getString(R.string.settings_sound_default),
        SettingsStore.NOTIFY_SOUND_APP to context.getString(R.string.settings_sound_classic),
        SettingsStore.NOTIFY_SOUND_DRAGON to context.getString(R.string.settings_sound_dragon),
        SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_09 to context.getString(R.string.settings_sound_chime),
        SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_062 to context.getString(R.string.settings_sound_bubble)
    )
    var showSim by remember(revision) { mutableStateOf(vm.settings.showSimIndicator) }
    val themeMode = vm.themeMode

    var soundDialog by remember { mutableStateOf(false) }

    var pinned by remember(revision) { mutableStateOf(vm.settings.pinnedEnabled) }
    var archiving by remember(revision) { mutableStateOf(vm.settings.archivingEnabled) }
    var swipeActions by remember(revision) { mutableStateOf(vm.settings.swipeActionsEnabled) }
    var blocking by remember(revision) { mutableStateOf(vm.settings.blockingEnabled) }
    val privacyMode by remember(revision) { mutableStateOf(vm.settings.privacyModeEnabled) }
    var forwarding by remember(revision) { mutableStateOf(vm.settings.forwardingEnabled) }
    var unreadAtTop by remember(revision) { mutableStateOf(vm.settings.unreadAtTopEnabled) }
    var scheduledMessages by remember(revision) { mutableStateOf(vm.settings.scheduledMessagesEnabled) }
    var delayedSending by remember(revision) { mutableStateOf(vm.settings.delayedSendingEnabled) }
    var delaySeconds by remember(revision) { mutableIntStateOf(vm.settings.delaySeconds) }

    var simDialog by remember { mutableStateOf(false) }
    var sims by remember { mutableStateOf(emptyList<SimCard>()) }
    var backingUp by remember { mutableStateOf(false) }
    var delayDialog by remember { mutableStateOf(false) }
    var backupFolder by remember(revision) { mutableStateOf(vm.settings.backupTreeUri) }

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

    LaunchedEffect(Unit) {
        sims = SimCards.load(context)
        // Heal a saved subscription that no longer exists (SIM removed, or the
        // debug fake list was turned off) back to the system default.
        val effective = SimSelection.effective(vm.settings.simSubscriptionId, sims)
        if (effective != vm.settings.simSubscriptionId) vm.settings.simSubscriptionId = effective
    }

    val simPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sims = SimCards.load(context)
            simDialog = true
        }
    }

    val showImportResult: (com.anindra.messages.data.Repository.ImportResult) -> Unit = { result ->
        val msg = when (result) {
            is com.anindra.messages.data.Repository.ImportResult.Success ->
                if (result.merged != null) String.format(context.getString(R.string.settings_restored_msg), result.merged)
                else context.getString(R.string.settings_backup_restored)
            is com.anindra.messages.data.Repository.ImportResult.Error ->
                String.format(context.getString(R.string.settings_import_failed), result.message)
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

    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Only persist a location we can actually keep using after reboot.
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                vm.settings.backupTreeUri = uri.toString()
                backupFolder = uri.toString()
            } catch (_: SecurityException) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_backup_location_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_general)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.icon_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_notif_title),
                    subtitle = stringResource(R.string.settings_notif_subtitle),
                    checked = notifications,
                    onChecked = { notifications = it; vm.settings.notificationsEnabled = it }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_pin_notification_sound),
                    subtitle = notificationSoundLabel(notificationSound, notificationSoundOptions, context),
                    onClick = {
                        notificationSound = vm.settings.notificationSound
                        soundDialog = true
                    }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_delivery_reports_title),
                    subtitle = stringResource(R.string.settings_delivery_reports_subtitle),
                    checked = delivery,
                    onChecked = { delivery = it; vm.settings.deliveryReportsEnabled = it }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_mark_read_title),
                    subtitle = stringResource(R.string.settings_mark_read_subtitle),
                    onClick = {
                        vm.markAllRead()
                        Toast.makeText(context, context.getString(R.string.settings_mark_read), Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_theme_title),
                    subtitle = themeLabel(themeMode, context),
                    onClick = { themeDialog = true }
                )
                val selectedSub = sims.firstOrNull { it.subscriptionId == vm.settings.simSubscriptionId }
                val currentSimLabel = if (selectedSub == null) {
                    context.getString(R.string.sim_default)
                } else {
                    when (val label = SimLabels.resolve(
                        selectedSub.subscriptionId, selectedSub.slotIndex, selectedSub.carrierName
                    )) {
                        SimLabel.Default -> context.getString(R.string.sim_default)
                        is SimLabel.Carrier -> String.format(
                            context.getString(R.string.settings_sim_label_format), label.carrier, label.slot
                        )
                        is SimLabel.Slot -> String.format(
                            context.getString(R.string.settings_sim_label), label.slot
                        )
                        SimLabel.Unknown -> context.getString(R.string.sim_default)
                    }
                }
                SettingsRow(
                    title = stringResource(R.string.settings_pin_sim_card),
                    subtitle = currentSimLabel,
                    onClick = {
                        val hasPerm = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                                PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            sims = SimCards.load(context)
                            simDialog = true
                        } else {
                            simPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                        }
                    }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_sim_indicator),
                    subtitle = stringResource(R.string.settings_sim_indicator_desc),
                    checked = showSim,
                    onChecked = { showSim = it; vm.settings.showSimIndicator = it }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_archiving_title),
                    subtitle = stringResource(R.string.settings_archiving_subtitle),
                    checked = archiving,
                    onChecked = { archiving = it; vm.settings.archivingEnabled = it }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_pinned_title),
                    subtitle = stringResource(R.string.settings_pinned_subtitle),
                    checked = pinned,
                    onChecked = { pinned = it; vm.settings.pinnedEnabled = it; if (!it) vm.unpinAll() }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_swipe_actions_title),
                    subtitle = stringResource(R.string.settings_swipe_actions_subtitle),
                    checked = swipeActions,
                    onChecked = { swipeActions = it; vm.settings.swipeActionsEnabled = it }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_unread_top_title),
                    subtitle = stringResource(R.string.settings_unread_top_subtitle),
                    checked = unreadAtTop,
                    onChecked = { unreadAtTop = it; vm.settings.unreadAtTopEnabled = it }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_forwarding_title),
                    subtitle = stringResource(R.string.settings_forwarding_subtitle),
                    checked = forwarding,
                    onChecked = { forwarding = it; vm.settings.forwardingEnabled = it }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_scheduled_title),
                    subtitle = stringResource(R.string.settings_scheduled_subtitle),
                    checked = scheduledMessages,
                    onChecked = { scheduledMessages = it; vm.settings.scheduledMessagesEnabled = it }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_delayed_title),
                    subtitle = stringResource(R.string.settings_delayed_subtitle),
                    checked = delayedSending,
                    onChecked = {
                        delayedSending = it; vm.settings.delayedSendingEnabled = it
                        if (it) delayDialog = true
                    }
                )
                if (delayedSending) {
                    SettingsRow(
                        title = stringResource(R.string.settings_delay_secs_title),
                        subtitle = String.format(context.getString(R.string.settings_delay_with_value), delaySeconds),
                        onClick = { delayDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_blocking_title),
                    subtitle = stringResource(R.string.settings_blocking_subtitle),
                    checked = blocking,
                    onChecked = { blocking = it; vm.settings.blockingEnabled = it }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_trash_title),
                    subtitle = stringResource(R.string.settings_trash_subtitle),
                    onClick = onOpenTrash
                )
                SettingsRow(
                    title = stringResource(R.string.conversations_spam_blocked),
                    subtitle = stringResource(R.string.settings_spam_blocked_subtitle),
                    onClick = onOpenSpamBlocked
                )
                SettingsRow(
                    title = stringResource(R.string.settings_backup_title),
                    subtitle = when {
                        privacyMode -> stringResource(R.string.settings_backup_privacy_disabled)
                        backingUp -> stringResource(R.string.settings_saving)
                        else -> stringResource(R.string.settings_backup_saving)
                    },
                    enabled = !privacyMode,
                    onClick = {
                        pinMode = PinDialogMode.SET
                        pinInput = ""
                        pinConfirm = ""
                        pinError = null
                    }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_import_title),
                    subtitle = stringResource(R.string.settings_import_subtitle),
                    onClick = {
                        pendingImportMode = com.anindra.messages.data.ImportMode.MERGE
                        importLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3"))
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_advanced_title),
                    subtitle = stringResource(R.string.settings_advanced_subtitle),
                    onClick = onOpenAdvanced
                )
            }

            val scheduledMsgs by vm.scheduledMessages().collectAsState(initial = emptyList())
            if (scheduledMsgs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    val is24Hour = is24HourFormat(context)
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
                                    "To: ${formatPhoneNumber(sm.address)} · ${formatDateTime(sm.timestamp, "MMM d,", is24Hour)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { vm.cancelScheduledMessage(sm.id) },
                                modifier = Modifier.size(A11y.touchTarget(32.dp))
                            ) {
                                Icon(Icons.Rounded.Cancel, stringResource(R.string.icon_cancel), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.messages_footer),
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
            title = { Text(stringResource(R.string.settings_choose_theme)) },
            text = {
                Column {
                    listOf(
                        "light" to stringResource(R.string.settings_theme_light),
                        "dark" to stringResource(R.string.settings_theme_dark),
                        "system" to stringResource(R.string.settings_theme_system)
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
                TextButton(onClick = { themeDialog = false }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { themeDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (soundDialog) {
        AlertDialog(
            onDismissRequest = { soundDialog = false },
            title = { Text(stringResource(R.string.settings_pin_notification_sound)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_sound_arrival),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    notificationSoundOptions.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    notificationSound = value
                                    NotificationHelper.previewNotificationSound(context, value)
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = notificationSound == value,
                                onClick = {
                                    notificationSound = value
                                    NotificationHelper.previewNotificationSound(context, value)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.settings.notificationSound = notificationSound
                    NotificationHelper.ensureChannel(context)
                    soundDialog = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { soundDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (simDialog) {
        var selected by remember { mutableIntStateOf(vm.settings.simSubscriptionId) }
        val options = mutableListOf(-1 to context.getString(R.string.settings_sim_default))
        sims.forEach { sub ->
            val carrier = sub.carrierName?.ifBlank { null }
            val label = if (carrier != null) String.format(context.getString(R.string.settings_sim_label_format), carrier, sub.slotIndex + 1) else String.format(context.getString(R.string.settings_sim_label), sub.slotIndex + 1)
            options += sub.subscriptionId to label
        }
        AlertDialog(
            onDismissRequest = { simDialog = false },
            title = { Text(stringResource(R.string.settings_pin_sim_card)) },
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
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { simDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (delayDialog) {
        val options = listOf(1, 3, 5, 10, 30)
        AlertDialog(
            onDismissRequest = { delayDialog = false },
            title = { Text(stringResource(R.string.settings_pin_delay)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_choose_delay),
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
                            Text(context.getString(R.string.settings_countdown_placeholder, secs))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { delayDialog = false }) { Text(stringResource(R.string.common_cancel)) }
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
            title = { Text(stringResource(R.string.settings_import_backup)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_backup_apply),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ImportChoiceRow(
                        title = stringResource(R.string.settings_merge_title),
                        subtitle = stringResource(R.string.settings_merge_subtitle),
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
                        title = stringResource(R.string.settings_restore_title),
                        subtitle = stringResource(R.string.settings_restore_subtitle),
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
                }) { Text(stringResource(R.string.common_cancel)) }
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
                    if (mode == PinDialogMode.SET) stringResource(R.string.settings_pin_dialog_set)
                    else stringResource(R.string.settings_pin_dialog_enter)
                )
            },
            text = {
                Column {
                    if (mode == PinDialogMode.SET) {
                        Text(
                            stringResource(R.string.settings_backup_pin_protection),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    backupFolderLauncher.launch(
                                        if (backupFolder.isBlank()) null else Uri.parse(backupFolder)
                                    )
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.settings_backup_save_to),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (BackupLocation.isCustom(backupFolder)) BackupLocation.label(backupFolder)
                                    else context.getString(R.string.settings_backup_location_default_option)
                                )
                            }
                            Text(
                                stringResource(R.string.settings_backup_change),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { new ->
                            if (new.length <= 16 && new.all { it.isDigit() }) pinInput = new
                        },
                        label = { Text(stringResource(R.string.settings_pin_enter)) },
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
                            label = { Text(stringResource(R.string.settings_pin_repeat)) },
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
                        pin.length < 4 -> pinError = context.getString(R.string.settings_pin_error_too_short)
                        mode == PinDialogMode.SET && pin != pinConfirm ->
                            pinError = context.getString(R.string.settings_pin_error_mismatch)
                        else -> {
                            if (mode == PinDialogMode.SET) {
                                pinMode = null
                                backingUp = true
                                vm.backupDatabase(pin) { ok ->
                                    backingUp = false
                                    val location = if (BackupLocation.isCustom(backupFolder)) BackupLocation.label(backupFolder)
                                    else context.getString(R.string.settings_backup_location_default)
                                    Toast.makeText(
                                        context,
                                        if (ok) String.format(context.getString(R.string.settings_backup_saved_location), location)
                                        else context.getString(R.string.settings_backup_failed),
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
                }) { Text(if (mode == PinDialogMode.SET) stringResource(R.string.settings_save) else stringResource(R.string.settings_import)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pinMode = null
                    pendingImportUri = null
                }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    val importLoading = vm.importLoading.value
    if (importLoading != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.settings_loading)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(context.getString(R.string.settings_loading_progress, importLoading), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
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

private fun themeLabel(mode: String, context: android.content.Context) = when (mode) {
    "light" -> context.getString(R.string.settings_theme_light)
    "dark" -> context.getString(R.string.settings_theme_dark)
    else -> context.getString(R.string.settings_theme_system)
}



private fun notificationSoundLabel(value: String, options: List<Pair<String, String>>, context: android.content.Context) =
    options.firstOrNull { it.first == value }?.second ?: context.getString(R.string.settings_sound_default)

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
fun SettingsRow(
    title: String,
    subtitle: String?,
    checked: Boolean? = null,
    onChecked: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (LocalLargeTouchTargets.current) 72.dp else 60.dp)
            .clickable(enabled = enabled && (onClick != null || checked != null)) {
                if (checked != null && onChecked != null) onChecked(!checked) else onClick?.invoke()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f).alpha(contentAlpha)) {
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
                enabled = enabled,
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
