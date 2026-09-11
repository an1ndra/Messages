package com.anindra.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.AlertDialog
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
                    title = "Permanent delete",
                    subtitle = "Delete messages immediately instead of moving them to trash",
                    checked = permanentDelete,
                    onChecked = {
                        permanentDelete = it
                        vm.settings.permanentDeleteEnabled = it
                    }
                )
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
                    title = "Highlight links",
                    subtitle = "Tap links in messages to open the website",
                    checked = highlightLinks,
                    onChecked = {
                        highlightLinks = it
                        vm.settings.highlightLinks = it
                    }
                )
                SettingsRow(
                    title = "Link open warning",
                    subtitle = "Confirm before opening external links",
                    checked = linkWarning,
                    onChecked = {
                        linkWarning = it
                        vm.settings.linkOpenWarningEnabled = it
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun PermanentDeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
        title = { Text("Delete permanently?") },
        text = {
            Text(
                "Permanent delete is on. This conversation and its messages will be " +
                    "removed from your device right away — not moved to trash — and cannot be restored."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}