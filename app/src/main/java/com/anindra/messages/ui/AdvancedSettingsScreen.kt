package com.anindra.messages.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.R
import com.anindra.messages.data.RetentionPolicy
import com.anindra.messages.data.SettingsStore
import com.anindra.messages.diagnostics.DiagnosticsDialog
import com.anindra.messages.sms.NotificationHelper
import com.anindra.messages.ui.theme.AppFonts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenAccessibility: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenAutoDelete: () -> Unit = {},
    onOpenLinks: () -> Unit = {},
    onOpenMmsCheck: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val revision by vm.settings.revision.collectAsState()
    var hideLinks by remember(revision) { mutableStateOf(vm.settings.hideLinks) }
    var highlightLinks by remember(revision) { mutableStateOf(vm.settings.highlightLinks) }
    var linkWarning by remember(revision) { mutableStateOf(vm.settings.linkOpenWarningEnabled) }
    var emojiButton by remember(revision) { mutableStateOf(vm.settings.emojiButtonEnabled) }
    var sendSound by remember(revision) { mutableStateOf(vm.settings.sendSoundEnabled) }
    var receiveSound by remember(revision) { mutableStateOf(vm.settings.receiveSoundEnabled) }
    var retentionDaysDialog by remember { mutableStateOf(false) }
    var daysTarget by remember { mutableStateOf("") }
    var retentionOn by remember(revision) { mutableStateOf(vm.settings.retentionEnabled) }
    var retentionTrash by remember(revision) { mutableStateOf(vm.settings.retentionTrash) }
    var retentionKeyword by remember(revision) { mutableStateOf(vm.settings.retentionKeywordMessages) }
    var retentionBlocked by remember(revision) { mutableStateOf(vm.settings.retentionBlockedSenders) }
    var retentionTrashDays by remember(revision) { mutableStateOf(vm.settings.retentionTrashDays) }
    var retentionSpamDays by remember(revision) { mutableStateOf(vm.settings.retentionSpamDays) }
    var fontDialog by remember { mutableStateOf(false) }
    var keywordsDialog by remember { mutableStateOf(false) }
    var diagReport by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_advanced_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsLayout.SCREEN_PADDING)
        ) {
            Spacer(Modifier.height(SettingsLayout.TOP_GAP))

            SettingsGroup {
                SettingsRow(
                position = RowPosition.FIRST,
                title = stringResource(R.string.settings_link_behaviour_title),
                subtitle = stringResource(R.string.settings_link_behaviour_subtitle),
                onClick = onOpenLinks
                )
                SettingsRow(
                position = RowPosition.MIDDLE,
                title = stringResource(R.string.keywords_title),
                subtitle = blockedKeywordsSubtitle(vm.settings.blockedKeywords),
                onClick = { keywordsDialog = true }
                )
                SettingsRow(
                position = RowPosition.MIDDLE,
                title = stringResource(R.string.settings_notif_title),
                subtitle = stringResource(R.string.settings_notif_subtitle),
                onClick = onOpenNotifications
                )
                SettingsRow(
                position = RowPosition.MIDDLE,
                title = stringResource(R.string.settings_font_title),
                subtitle = fontLabel(vm.fontFamily),
                onClick = { fontDialog = true }
                )
                SettingsRow(
                position = RowPosition.MIDDLE,
                title = stringResource(R.string.settings_advanced_emoji_button),
                checked = emojiButton,
                onChecked = { emojiButton = it; vm.settings.emojiButtonEnabled = it }
                )
                SettingsRow(
                position = RowPosition.MIDDLE,
                title = stringResource(R.string.settings_accessibility_title),
                checked = vm.a11y.enabled,
                onChecked = { vm.a11yEnabled = it }
                )
                if (vm.a11y.enabled) {
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.accessibility_options_title),
                    onClick = onOpenAccessibility
                )
                }
                SettingsRow(
                position = RowPosition.MIDDLE,
                title = stringResource(R.string.settings_retention_title),
                subtitle = stringResource(R.string.settings_retention_subtitle),
                onClick = onOpenAutoDelete
                )
                SettingsRow(
                position = RowPosition.MIDDLE,
                title = stringResource(R.string.mms_check_title),
                subtitle = stringResource(R.string.mms_check_subtitle),
                onClick = onOpenMmsCheck
                )
                SettingsRow(
                position = RowPosition.LAST,
                title = stringResource(R.string.diagnostics_title),
                onClick = { vm.diagnosticsReport { diagReport = it } }
                )
            }
            Spacer(Modifier.height(SettingsLayout.GROUP_GAP))
        }
    }

    if (retentionDaysDialog) {
        val editingTrash = daysTarget == DAYS_TRASH
        val currentDays = if (editingTrash) retentionTrashDays else retentionSpamDays
        AlertDialog(
            onDismissRequest = { retentionDaysDialog = false },
            title = { Text(stringResource(R.string.settings_retention_after)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_choose_retention),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    RetentionPolicy.DAY_OPTIONS.forEach { days ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (editingTrash) {
                                        retentionTrashDays = days
                                        vm.settings.retentionTrashDays = days
                                    } else {
                                        retentionSpamDays = days
                                        vm.settings.retentionSpamDays = days
                                    }
                                    retentionDaysDialog = false
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = currentDays == days,
                                onClick = {
                                    if (editingTrash) {
                                        retentionTrashDays = days
                                        vm.settings.retentionTrashDays = days
                                    } else {
                                        retentionSpamDays = days
                                        vm.settings.retentionSpamDays = days
                                    }
                                    retentionDaysDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_retention_days, days))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { retentionDaysDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (fontDialog) {
        var selectedFont by remember { mutableStateOf(vm.fontFamily) }
        AlertDialog(
            onDismissRequest = { fontDialog = false },
            title = { Text(stringResource(R.string.settings_choose_font)) },
            text = {
                Column {
                    AppFonts.options.forEach { key ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFont = key }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedFont == key,
                                onClick = { selectedFont = key }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(fontLabel(key))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.fontFamily = selectedFont
                    fontDialog = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { fontDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (keywordsDialog) {
        BlockedKeywordsDialog(
            keywords = vm.settings.blockedKeywords.sorted(),
            onAdd = { vm.addBlockedKeyword(it) },
            onRemove = { vm.removeBlockedKeyword(it) },
            onDismiss = { keywordsDialog = false }
        )
    }

    val report = diagReport
    if (report != null) {
        DiagnosticsDialog(
            report = report,
            onCopy = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(
                    ClipData.newPlainText(context.getString(R.string.diagnostics_clip_label), report)
                )
            },
            onDismiss = { diagReport = null }
        )
    }
}

@Composable
private fun fontLabel(key: String): String = when (key) {
    SettingsStore.FONT_DM_SANS -> stringResource(R.string.font_dm_sans)
    SettingsStore.FONT_INTER -> stringResource(R.string.font_inter)
    SettingsStore.FONT_FIGTREE -> stringResource(R.string.font_figtree)
    SettingsStore.FONT_POPPINS -> stringResource(R.string.font_poppins)
    else -> stringResource(R.string.font_system)
}

@Composable
private fun blockedKeywordsSubtitle(keywords: Set<String>): String =
    if (keywords.isEmpty()) stringResource(R.string.keywords_subtitle_none)
    else stringResource(R.string.keywords_subtitle_count, keywords.size)

@Composable
fun PermanentDeleteConfirmDialog(
    onConfirm: (dontShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_advanced_delete_permanently)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.settings_advanced_permanent_delete_on)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth().clickable { dontShowAgain = !dontShowAgain }
                ) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_advanced_warning))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dontShowAgain) }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

private const val DAYS_TRASH = "trash"
private const val DAYS_SPAM = "spam"
