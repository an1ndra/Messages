package com.anindra.messages.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
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
import com.anindra.messages.data.SettingsStore
import com.anindra.messages.diagnostics.DiagnosticsDialog
import com.anindra.messages.sms.NotificationHelper
import com.anindra.messages.ui.theme.AppFonts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val revision by vm.settings.revision.collectAsState()
    var permanentDelete by remember(revision) { mutableStateOf(vm.settings.permanentDeleteEnabled) }
    var reverseSwipe by remember(revision) { mutableStateOf(vm.settings.reverseSwipeEnabled) }
    var hideLinks by remember(revision) { mutableStateOf(vm.settings.hideLinks) }
    var highlightLinks by remember(revision) { mutableStateOf(vm.settings.highlightLinks) }
    var linkWarning by remember(revision) { mutableStateOf(vm.settings.linkOpenWarningEnabled) }
    var privacyMode by remember(revision) { mutableStateOf(vm.settings.privacyModeEnabled) }
    var appLock by remember(revision) { mutableStateOf(vm.settings.appLockEnabled) }
    var drafts by remember(revision) { mutableStateOf(vm.settings.draftsEnabled) }
    var sendSound by remember(revision) { mutableStateOf(vm.settings.sendSoundEnabled) }
    var receiveSound by remember(revision) { mutableStateOf(vm.settings.receiveSoundEnabled) }
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
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Conversations
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_drafts_title),
                    subtitle = stringResource(R.string.settings_drafts_subtitle),
                    checked = drafts,
                    onChecked = { drafts = it; vm.settings.draftsEnabled = it }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_advanced_reverse_swipe),
                    subtitle = stringResource(R.string.settings_advanced_reverse_swipe_desc),
                    checked = reverseSwipe,
                    onChecked = {
                        reverseSwipe = it
                        vm.settings.reverseSwipeEnabled = it
                    }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_advanced_permanent_delete),
                    subtitle = stringResource(R.string.settings_advanced_permanent_delete_desc),
                    checked = permanentDelete,
                    onChecked = {
                        permanentDelete = it
                        vm.settings.permanentDeleteEnabled = it
                        if (!it) vm.settings.permanentDeleteWarn = true
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Links
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_advanced_hide_links),
                    subtitle = stringResource(R.string.settings_advanced_hide_links_desc),
                    checked = hideLinks,
                    onChecked = {
                        hideLinks = it
                        vm.settings.hideLinks = it
                    }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_advanced_highlight_links),
                    subtitle = if (hideLinks) {
                        stringResource(R.string.settings_advanced_turn_off) + stringResource(R.string.settings_advanced_hide_links) + stringResource(R.string.settings_advanced_turn_off_suffix)
                    } else {
                        stringResource(R.string.settings_link_tap_info)
                    },
                    checked = highlightLinks,
                    onChecked = {
                        highlightLinks = it
                        vm.settings.highlightLinks = it
                    },
                    enabled = !hideLinks
                )
                SettingsRow(
                    title = stringResource(R.string.settings_advanced_link_warning),
                    subtitle = when {
                        hideLinks -> stringResource(R.string.settings_advanced_turn_off) + stringResource(R.string.settings_advanced_hide_links) + stringResource(R.string.settings_advanced_turn_off_suffix)
                        !highlightLinks -> "Turn on \"Highlight links\" first"
                        else -> stringResource(R.string.link_warning_confirm)
                    },
                    checked = linkWarning,
                    onChecked = {
                        linkWarning = it
                        vm.settings.linkOpenWarningEnabled = it
                    },
                    enabled = !hideLinks && highlightLinks
                )
            }

            Spacer(Modifier.height(8.dp))

            // Privacy & security
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_privacy_title),
                    subtitle = stringResource(R.string.settings_privacy_subtitle),
                    checked = privacyMode,
                    onChecked = {
                        privacyMode = it
                        (context as? Activity)?.let { act -> vm.setPrivacyMode(act, it) }
                    }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_applock_title),
                    subtitle = stringResource(R.string.settings_applock_subtitle),
                    checked = appLock,
                    onChecked = { enable ->
                        val canAuth = BiometricManager.from(context).canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        if (enable && canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.lock_setup_needed),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            appLock = enable
                            vm.settings.appLockEnabled = enable
                        }
                    }
                )
                SettingsRow(
                    title = stringResource(R.string.keywords_title),
                    subtitle = blockedKeywordsSubtitle(vm.settings.blockedKeywords),
                    onClick = { keywordsDialog = true }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Notifications
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_send_sound_title),
                    subtitle = stringResource(R.string.settings_send_sound_subtitle),
                    checked = sendSound,
                    onChecked = { sendSound = it; vm.settings.sendSoundEnabled = it }
                )
                SettingsRow(
                    title = stringResource(R.string.settings_receive_sound_title),
                    subtitle = stringResource(R.string.settings_receive_sound_subtitle),
                    checked = receiveSound,
                    onChecked = {
                        receiveSound = it
                        vm.settings.receiveSoundEnabled = it
                        NotificationHelper.ensureChannel(context)
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Appearance
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_font_title),
                    subtitle = fontLabel(vm.fontFamily),
                    onClick = { fontDialog = true }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Support
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.diagnostics_title),
                    subtitle = stringResource(R.string.diagnostics_subtitle),
                    onClick = { vm.diagnosticsReport { diagReport = it } }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
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
