package com.anindra.messages.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.anindra.messages.R
import com.anindra.messages.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val revision by vm.settings.revision.collectAsState()
    var permanentDelete by remember(revision) { mutableStateOf(vm.settings.permanentDeleteEnabled) }
    var reverseSwipe by remember(revision) { mutableStateOf(vm.settings.reverseSwipeEnabled) }
    var hideLinks by remember(revision) { mutableStateOf(vm.settings.hideLinks) }
    var highlightLinks by remember(revision) { mutableStateOf(vm.settings.highlightLinks) }
    var linkWarning by remember(revision) { mutableStateOf(vm.settings.linkOpenWarningEnabled) }

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

            SettingsGroup {
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
        }
    }
}

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