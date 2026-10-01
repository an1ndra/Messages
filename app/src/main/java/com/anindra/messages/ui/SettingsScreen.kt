package com.anindra.messages.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
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

/** Which backup format the picked file belongs to. */
private enum class ImportSource { OWN_BACKUP, SMS_IE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenTrash: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
    onOpenSpamBlocked: () -> Unit = {},
    onOpenInbox: () -> Unit = {},
    onOpenScheduled: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState()
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current

    val revision by vm.settings.revision.collectAsState()
    val scheduledAll by vm.scheduledMessages().collectAsState(initial = emptyList())
    val scheduledCount = if (scheduledAll.isEmpty()) null
    else context.getString(R.string.settings_scheduled_count, scheduledAll.size)

    var themeDialog by remember { mutableStateOf(false) }
    var notificationSound by remember(revision) { mutableStateOf(vm.settings.notificationSound) }
    val notificationSoundOptions = listOf(
        SettingsStore.NOTIFY_SOUND_DEFAULT to context.getString(R.string.settings_sound_default),
        SettingsStore.NOTIFY_SOUND_APP to context.getString(R.string.settings_sound_classic),
        SettingsStore.NOTIFY_SOUND_DRAGON to context.getString(R.string.settings_sound_dragon),
        SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_09 to context.getString(R.string.settings_sound_chime),
        SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_062 to context.getString(R.string.settings_sound_bubble)
    )
    val themeMode = vm.themeMode

    var soundDialog by remember { mutableStateOf(false) }

    var privacyMode by remember(revision) { mutableStateOf(vm.settings.privacyModeEnabled) }
    var appLock by remember(revision) { mutableStateOf(vm.settings.appLockEnabled) }

    var simDialog by remember { mutableStateOf(false) }
    var sims by remember { mutableStateOf(emptyList<SimCard>()) }
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
    var importSourceDialog by remember { mutableStateOf(false) }
    var pendingImportSource by remember { mutableStateOf(ImportSource.OWN_BACKUP) }
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
                pendingImportSource = ImportSource.OWN_BACKUP
                importModeDialog = true
            }
        }
    }

    val smsIeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
            pendingImportSource = ImportSource.SMS_IE
            importModeDialog = true
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
                .padding(horizontal = SettingsLayout.SCREEN_PADDING)
        ) {
            Spacer(Modifier.height(SettingsLayout.TOP_GAP))

            SettingsGroup {
                SettingsRow(
                    position = RowPosition.FIRST,
                    title = stringResource(R.string.settings_notif_title),
                    onClick = { openSystemNotificationSettings(context) }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_mark_read_title),
                    onClick = {
                        vm.markAllRead()
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_mark_read),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
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
                    position = RowPosition.MIDDLE,
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
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_inbox_title),
                    onClick = onOpenInbox
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_scheduled_title),
                    subtitle = scheduledCount,
                    onClick = onOpenScheduled
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_privacy_title),
                    checked = privacyMode,
                    onChecked = {
                        privacyMode = it
                        (context as? Activity)?.let { act -> vm.setPrivacyMode(act, it) }
                    }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_applock_title),
                    subtitle = stringResource(R.string.settings_applock_subtitle),
                    checked = appLock,
                    onChecked = { enable ->
                        verifyForAppLockChange(context) { verdict ->
                            when (verdict) {
                                AppLockVerdict.Verified -> {
                                    appLock = enable
                                    vm.settings.appLockEnabled = enable
                                }
                                AppLockVerdict.NoScreenLock -> Toast.makeText(
                                    context,
                                    context.getString(R.string.lock_setup_needed),
                                    Toast.LENGTH_LONG
                                ).show()
                                AppLockVerdict.Rejected -> Unit
                            }
                        }
                    }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_trash_title),
                    onClick = onOpenTrash
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.conversations_spam_blocked),
                    onClick = onOpenSpamBlocked
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_backup_title),
                    subtitle = stringResource(R.string.settings_backup_subtitle),
                    enabled = !privacyMode,
                    onClick = {
                        pinMode = PinDialogMode.SET
                        pinInput = ""
                        pinConfirm = ""
                        pinError = null
                    }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.settings_import_title),
                    subtitle = stringResource(R.string.settings_import_subtitle),
                    onClick = { importSourceDialog = true }
                )
                SettingsRow(
                    position = RowPosition.LAST,
                    title = stringResource(R.string.settings_advanced_title),
                    subtitle = stringResource(R.string.settings_advanced_subtitle),
                    onClick = onOpenAdvanced
                )
            }

            SettingsFooter(stringResource(R.string.messages_footer))
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
                        "amoled" to stringResource(R.string.settings_theme_amoled),
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

    if (importSourceDialog) {
        var source by remember { mutableStateOf(ImportSource.OWN_BACKUP) }
        fun launchPicker() {
            importSourceDialog = false
            if (source == ImportSource.OWN_BACKUP) {
                importLauncher.launch(
                    arrayOf("application/octet-stream", "application/x-sqlite3")
                )
            } else {
                smsIeLauncher.launch(
                    arrayOf(
                        "application/zip",
                        "application/json",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            }
        }
        AlertDialog(
            onDismissRequest = { importSourceDialog = false },
            title = { Text(stringResource(R.string.settings_import_source_title)) },
            text = {
                ImportRadioGroup(
                    options = listOf(
                        stringResource(R.string.settings_import_source_own_title) to "",
                        stringResource(R.string.settings_import_source_sms_ie_title) to ""
                    ),
                    selectedIndex = if (source == ImportSource.OWN_BACKUP) 0 else 1,
                    onSelect = {
                        source = if (it == 0) ImportSource.OWN_BACKUP else ImportSource.SMS_IE
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { launchPicker() }) {
                    Text(stringResource(R.string.common_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { importSourceDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (importModeDialog) {
        val uri = pendingImportUri
        var mode by remember {
            mutableStateOf(com.anindra.messages.data.ImportMode.MERGE)
        }
        fun applyImport() {
            val target = uri ?: return
            if (pendingImportSource == ImportSource.SMS_IE) {
                vm.importSmsIe(target, mode) { count ->
                    showImportResult(
                        if (count < 0) {
                            com.anindra.messages.data.Repository.ImportResult.Error(
                                context.getString(R.string.settings_import_sms_ie_failed)
                            )
                        } else {
                            com.anindra.messages.data.Repository.ImportResult.Success(count)
                        }
                    )
                }
                return
            }
            when {
                pendingImportFormat == com.anindra.messages.data.BackupFormat.PIN -> {
                    pinInput = ""
                    pinError = null
                    pinMode = PinDialogMode.ENTER
                }
                else -> vm.importDatabase(target, null, mode, showImportResult)
            }
        }
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
                    ImportRadioGroup(
                        options = listOf(
                            stringResource(R.string.settings_merge_title) to
                                stringResource(R.string.settings_merge_subtitle),
                            stringResource(R.string.settings_restore_title) to
                                stringResource(R.string.settings_restore_subtitle)
                        ),
                        selectedIndex = if (mode == com.anindra.messages.data.ImportMode.MERGE) 0 else 1,
                        onSelect = {
                            mode = if (it == 0) {
                                com.anindra.messages.data.ImportMode.MERGE
                            } else {
                                com.anindra.messages.data.ImportMode.REPLACE
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    importModeDialog = false
                    pendingImportMode = mode
                    applyImport()
                }) { Text(stringResource(R.string.settings_import)) }
            },
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
                                vm.backupDatabase(pin) { ok ->
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

/**
 * A run of settings rows separated by a small gap. Each row draws its own card;
 * the group itself is only the vertical rhythm, so a group's outer rows are
 * strongly rounded while the rows between them stay nearly rectangular.
 */
/**
 * A card whose corner rounding follows its place in a group, for screens that
 * need their own row content but the same shape language as the settings list.
 * Pair with a [Column] of [SettingsLayout.ROW_GAP] spacing.
 */
@Composable
fun GroupedRowCard(
    position: RowPosition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val corners = rowCorners(position)
    Card(
        shape = RoundedCornerShape(
            topStart = corners.topStart,
            topEnd = corners.topEnd,
            bottomEnd = corners.bottomEnd,
            bottomStart = corners.bottomStart
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SettingsLayout.ROW_GAP)) {
        content()
    }
}

/**
 * One card holding several related rows, split by hairlines. Used where a list
 * reads as a single block rather than as a run of separate row cards, e.g. a
 * contact profile's notification and block controls.
 */
@Composable
fun GroupedRowList(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(SettingsLayout.OUTER_RADIUS),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

/** Android's own notification prefs for this app, which is where the OS-level
 *  toggles (per-conversation, channels, badges) actually live. */
private fun openSystemNotificationSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun themeLabel(mode: String, context: android.content.Context) = when (mode) {
    "light" -> context.getString(R.string.settings_theme_light)
    "dark" -> context.getString(R.string.settings_theme_dark)
    "amoled" -> context.getString(R.string.settings_theme_amoled)
    else -> context.getString(R.string.settings_theme_system)
}



private fun notificationSoundLabel(value: String, options: List<Pair<String, String>>, context: android.content.Context) =
    options.firstOrNull { it.first == value }?.second ?: context.getString(R.string.settings_sound_default)

/**
 * A single-choice group of [options] (label to optional supporting text),
 * following the Material 3 radio button guidelines: stacked vertically, one
 * option always selected, and the whole row is the tap target so either the
 * radio or its label selects. Selection does not act on its own - the dialog's
 * confirm button does.
 */
@Composable
internal fun ImportRadioGroup(
    options: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(Modifier.selectableGroup()) {
        options.forEachIndexed { index, (label, supporting) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(
                        selected = selectedIndex == index,
                        onClick = { onSelect(index) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                RadioButton(selected = selectedIndex == index, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * One settings row, drawn as its own card. [position] decides which corners
 * soften, so the first and last rows of a group are strongly rounded and the
 * ones between them nearly square, which makes a gap-separated run of cards
 * read as one group.
 */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean? = null,
    onChecked: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    position: RowPosition = RowPosition.SINGLE,
    trailing: (@Composable () -> Unit)? = null,
    preview: (@Composable () -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    val corners = rowCorners(position)
    Card(
        shape = RoundedCornerShape(
            topStart = corners.topStart,
            topEnd = corners.topEnd,
            bottomEnd = corners.bottomEnd,
            bottomStart = corners.bottomStart
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled && (onClick != null || checked != null)) {
                    if (checked != null && onChecked != null) onChecked(!checked) else onClick?.invoke()
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = SettingsLayout.rowMinHeight(
                            hasSubtitle = subtitle != null,
                            largeTouchTargets = LocalLargeTouchTargets.current
                        )
                    )
                    .padding(
                        horizontal = SettingsLayout.ROW_CONTENT_PADDING,
                        vertical = 12.dp
                    )
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
                    // M3 sizes the Switch for a 48dp touch target, which makes every
                    // switch row ~16dp taller than a title-only one and breaks the
                    // list's rhythm. The whole row is already the control, so the
                    // switch only has to render at its own 52x32 track size.
                    Switch(
                        checked = checked,
                        onCheckedChange = { onChecked(it) },
                        enabled = enabled,
                        modifier = Modifier.size(width = 52.dp, height = 32.dp)
                    )
                } else if (trailing != null) {
                    trailing()
                }
            }
            if (preview != null) {
                Box(
                    Modifier.padding(
                        start = SettingsLayout.PREVIEW_INSET,
                        end = SettingsLayout.PREVIEW_INSET,
                        bottom = SettingsLayout.PREVIEW_INSET
                    )
                ) { preview() }
            }
        }
    }
}

/**
 * Explanatory text under a settings group. Set on its own line rather than as a
 * row, so it reads as a note about the group instead of another option. Uses
 * the same type as a row's subtitle, since it describes those same options.
 */
@Composable
fun SettingsFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            // No horizontal inset: the note lines up with the left and right
            // edges of the row cards above it, the way a page's border does,
            // rather than with the text inside them.
            .padding(
                top = SettingsLayout.FOOTER_TOP_GAP,
                bottom = SettingsLayout.FOOTER_BOTTOM_GAP
            )
    )
}
