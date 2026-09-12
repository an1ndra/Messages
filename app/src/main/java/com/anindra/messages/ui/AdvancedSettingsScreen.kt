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
                title = { Text("Advanced settings") },
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
                    title = "Reverse swipe actions",
                    subtitle = "Swipe left to archive and right to delete",
                    checked = reverseSwipe,
                    onChecked = {
                        reverseSwipe = it
                        vm.settings.reverseSwipeEnabled = it
                    }
                )
                SettingsRow(
                    title = "Hide links from messages",
                    subtitle = "Never turn links in messages into tappable links",
                    checked = hideLinks,
                    onChecked = {
                        hideLinks = it
                        vm.settings.hideLinks = it
                    }
                )
                SettingsRow(
                    title = "Highlight links",
                    subtitle = if (hideLinks) {
                        "Turn off \"Hide links from messages\" first"
                    } else {
                        "Tap links in messages to open the website"
                    },
                    checked = highlightLinks,
                    onChecked = {
                        highlightLinks = it
                        vm.settings.highlightLinks = it
                    },
                    enabled = !hideLinks
                )
                SettingsRow(
                    title = "Link open warning",
                    subtitle = when {
                        hideLinks -> "Turn off \"Hide links from messages\" first"
                        !highlightLinks -> "Turn on \"Highlight links\" first"
                        else -> "Confirm before opening external links"
                    },
                    checked = linkWarning,
                    onChecked = {
                        linkWarning = it
                        vm.settings.linkOpenWarningEnabled = it
                    },
                    enabled = !hideLinks && highlightLinks
                )
                SettingsRow(
                    title = "Permanent delete",
                    subtitle = "Delete messages immediately instead of moving them to trash",
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
        title = { Text("Delete permanently?") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Permanent delete is on. This conversation and its messages will be " +
                        "removed from your device right away — not moved to trash — and cannot be restored."
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth().clickable { dontShowAgain = !dontShowAgain }
                ) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Don't show this warning again")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dontShowAgain) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}