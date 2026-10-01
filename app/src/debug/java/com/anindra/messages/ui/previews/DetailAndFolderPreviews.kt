package com.anindra.messages.ui.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.anindra.messages.crash.CrashReportDialog
import com.anindra.messages.diagnostics.DiagnosticsDialog
import com.anindra.messages.ui.AutoDeleteDurationAction
import com.anindra.messages.ui.BlockedKeywordsDialog
import com.anindra.messages.ui.DetailActionButton
import com.anindra.messages.ui.DetailCardRow
import com.anindra.messages.ui.EmptyFolder
import com.anindra.messages.ui.GroupedRowCard
import com.anindra.messages.ui.ImportRadioGroup
import com.anindra.messages.ui.PermanentDeleteConfirmDialog
import com.anindra.messages.ui.ReschedulePicker
import com.anindra.messages.ui.RowPosition
import com.anindra.messages.ui.SettingsFooter
import com.anindra.messages.ui.SettingsScaffold
import com.anindra.messages.ui.legacy.SettingsSearchResults
import com.anindra.messages.ui.TrashMessageRow
import com.anindra.messages.ui.TrashReasonTag
import com.anindra.messages.ui.TrashRow

@PreviewLightDark
@Preview(name = "Contact detail rows", showBackground = true, widthDp = 380)
@Composable
private fun DetailRowsPreview() {
    Stage {
        DetailCardRow(Icons.Rounded.Person, "Priya Raman")
        DetailCardRow(Icons.Rounded.Call, "Call", onClick = {})
        DetailCardRow(
            Icons.Rounded.Flag,
            "Blocked",
            titleColor = MaterialTheme.colorScheme.error,
            iconColor = MaterialTheme.colorScheme.error
        )
        DetailCardRow(
            Icons.AutoMirrored.Rounded.Chat,
            "Send message",
            onClick = {},
            trailing = { DetailActionButton(Icons.Rounded.Call, "Call", {}) }
        )
    }
}

@PreviewLightDark
@Preview(name = "Detail action buttons", showBackground = true, widthDp = 380)
@Composable
private fun DetailActionButtonsPreview() {
    Stage {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailActionButton(Icons.Rounded.Call, "Call") {}
            DetailActionButton(Icons.AutoMirrored.Rounded.Chat, "Message") {}
            DetailActionButton(Icons.Rounded.DeleteForever, "Delete", onClick = {})
        }
    }
}

@PreviewLightDark
@Preview(name = "Settings scaffold", showBackground = true, widthDp = 380, heightDp = 300)
@Composable
private fun SettingsScaffoldPreview() {
    // The scaffold only exists to supply the top bar, so it is previewed with
    // a body to confirm the content insets sit below the bar.
    Stage {
        SettingsScaffold(title = "Advanced settings", onBack = {}) {
            SettingsFooter("Content is inset below the top bar.")
        }
    }
}

@PreviewLightDark
@Preview(name = "Settings grouped cards", showBackground = true, widthDp = 380)
@Composable
private fun GroupedRowCardPreview() {
    // Each RowPosition decides which corners soften, so a run of them is shown
    // together: the seam between two cards is the thing being checked.
    Stage {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                RowPosition.FIRST,
                RowPosition.MIDDLE,
                RowPosition.MIDDLE,
                RowPosition.LAST
            ).forEach { position ->
                GroupedRowCard(position) {
                    SettingsFooter("position = ${position.name.lowercase()}")
                }
            }
        }
    }
}

@PreviewLightDark
@Preview(name = "Import radio group", showBackground = true, widthDp = 380)
@Composable
private fun ImportRadioGroupPreview() {
    Stage {
        ImportRadioGroup(
            options = listOf("Keep a backup" to "Save messages to a local file"),
            selectedIndex = 0,
            onSelect = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Blocked keywords dialog", showBackground = true, widthDp = 380)
@Composable
private fun BlockedKeywordsDialogPreview() {
    Stage {
        BlockedKeywordsDialog(
            keywords = listOf("WIN", "prize", "claim now"),
            onAdd = {},
            onRemove = {},
            onDismiss = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Permanent delete confirmation", showBackground = true, widthDp = 380)
@Composable
private fun PermanentDeleteConfirmDialogPreview() {
    Stage { PermanentDeleteConfirmDialog(onConfirm = {}, onDismiss = {}) }
}

@PreviewLightDark
@Preview(name = "Crash report dialog", showBackground = true, widthDp = 380)
@Composable
private fun CrashReportDialogPreview() {
    Stage { CrashReportDialog(reportCount = 3, onExportZip = {}, onCopy = {}, onDelete = {}) }
}

@PreviewLightDark
@Preview(name = "Diagnostics dialog", showBackground = true, widthDp = 380, heightDp = 620)
@Composable
private fun DiagnosticsDialogPreview() {
    Stage {
        DiagnosticsDialog(
            report = """
                App version: 1.0.27
                Default SMS handler: true
                Device: vivo I2214 (Android 16, SDK 36)
                SIM 1: Jio
                  MCC/MNC: 40445
                  enabledMMS: true
                  maxMessageSize: 10485760
                SIM 2: Airtel
                  MCC/MNC: 40402
                  enabledMMS: absent
                  maxMessageSize: null
            """.trimIndent(),
            onCopy = {},
            onDismiss = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Auto delete durations", showBackground = true, widthDp = 380)
@Composable
private fun AutoDeleteDurationPreview() {
    Stage {
        // Inactive and active side by side: the active state is the only
        // difference, so it is easier to see wrong in a pair.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { days ->
                AutoDeleteDurationAction(days, active = days == 30) {}
            }
        }
    }
}

@PreviewLightDark
@Preview(name = "Trash rows", showBackground = true, widthDp = 380)
@Composable
private fun TrashRowsPreview() {
    Stage {
        TrashRow(
            convo = sampleConversation(1, "Priya Raman", "deleted conversation"),
            onRestore = {},
            onDeleteForever = {}
        )
        TrashMessageRow(
            msg = sampleTrashedMessage(1, "Priya Raman", "deleted message"),
            onRestore = {},
            onDeleteForever = {}
        )
        Row {
            TrashReasonTag()
        }
    }
}

@PreviewLightDark
@Preview(name = "Empty states", showBackground = true, widthDp = 380, heightDp = 300)
@Composable
private fun EmptyStatesPreview() {
    Stage {
        EmptyFolder(Icons.Rounded.Flag, "Nothing is blocked")
        EmptyFolder(Icons.Rounded.DeleteForever, "Trash is empty")
    }
}

@PreviewLightDark
@Preview(name = "Reschedule picker", showBackground = true, widthDp = 380)
@Composable
private fun ReschedulePickerPreview() {
    Stage { ReschedulePicker(timestamp = SAMPLE_TS, onDone = {}, onDismiss = {}) }
}

@PreviewLightDark
@Preview(name = "Settings search results", showBackground = true, widthDp = 380)
@Composable
private fun SettingsSearchResultsPreview() {
    // The indices are the ones SettingsSearch.LEGACY_OPTIONS assigns, so this
    // renders the same labels a real search would return.
    Stage {
        SettingsSearchResults(results = listOf(3, 9, 15), onPick = {})
    }
}
