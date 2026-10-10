package com.anindra.messages.ui.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.anindra.messages.data.Repository
import com.anindra.messages.data.TransferEntry
import com.anindra.messages.data.TransferOperation
import com.anindra.messages.ui.TransferEntryCard
import com.anindra.messages.ui.TransferLogEmpty
import com.anindra.messages.ui.TransferLogScreen
import com.anindra.messages.ui.TransferPill
import com.anindra.messages.ui.TransferStatusIcon
import com.anindra.messages.ui.theme.MessagesTheme

private val sampleEntries = listOf(
    TransferEntry(
        timestamp = 1_757_000_000_000L,
        operation = TransferOperation.IMPORT,
        format = "PIN",
        mode = "merge",
        succeeded = true,
        detail = "Merged 4,012 message(s)",
        added = 4012,
        seen = 4624,
        skipped = 612,
        conflicts = mapOf(
            Repository.Conflict.ALREADY_PRESENT to 600,
            Repository.Conflict.CONVERSATION_MERGED to 11,
            Repository.Conflict.PROVIDER_ID_DROPPED to 1
        )
    ),
    TransferEntry(
        timestamp = 1_756_999_500_000L,
        operation = TransferOperation.IMPORT,
        format = "RAW",
        mode = "replace",
        succeeded = true,
        detail = "Recovered an interrupted restore",
        recovered = true
    ),
    TransferEntry(
        timestamp = 1_756_999_000_000L,
        operation = TransferOperation.EXPORT,
        format = "RAW",
        mode = "none",
        succeeded = true,
        detail = "Saved messages_backup_1756999000000.db (after 3 attempts)",
        attempts = 3
    ),
    TransferEntry(
        timestamp = 1_756_998_000_000L,
        operation = TransferOperation.IMPORT,
        format = "sms-ie",
        mode = "merge",
        succeeded = true,
        detail = "Imported 3 of 5 message(s)",
        added = 3,
        seen = 5,
        skipped = 2,
        conflicts = mapOf(Repository.Conflict.PART_TOO_LARGE to 2)
    ),
    TransferEntry(
        timestamp = 1_756_997_000_000L,
        operation = TransferOperation.IMPORT,
        format = "PIN",
        mode = "replace",
        succeeded = false,
        detail = "Wrong PIN or corrupted file (after 3 attempts)",
        attempts = 3
    )
)

@PreviewLightDark
@Composable
private fun TransferLogScreenPreview() {
    MessagesTheme(mode = "system") {
        TransferLogScreen(
            entries = sampleEntries,
            onBack = {},
            onClear = {}
        )
    }
}

@PreviewLightDark
@Composable
private fun TransferLogScreenEmptyPreview() {
    MessagesTheme(mode = "system") {
        TransferLogScreen(entries = emptyList(), onBack = {}, onClear = {})
    }
}

/** One card on its own, so its rows can be checked without scrolling a list. */
@PreviewLightDark
@Composable
private fun TransferEntryCardFailedPreview() {
    MessagesTheme(mode = "system") {
        TransferEntryCard(
            TransferEntry(
                timestamp = 1_756_998_000_000L,
                operation = TransferOperation.IMPORT,
                format = "PIN",
                mode = "replace",
                succeeded = false,
                detail = "Wrong PIN or corrupted file"
            )
        )
    }
}

@PreviewLightDark
@Composable
private fun TransferEntryCardRetriedPreview() {
    MessagesTheme(mode = "system") {
        TransferEntryCard(
            TransferEntry(
                timestamp = 1_756_999_000_000L,
                operation = TransferOperation.EXPORT,
                format = "RAW",
                mode = "none",
                succeeded = true,
                detail = "Saved messages_backup_1756999000000.db",
                attempts = 3
            )
        )
    }
}

@PreviewLightDark
@Composable
private fun TransferStatusIconPreview() {
    MessagesTheme(mode = "system") {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TransferStatusIcon(ok = true)
            Spacer(Modifier.width(12.dp))
            TransferStatusIcon(ok = false)
        }
    }
}

@PreviewLightDark
@Composable
private fun TransferPillPreview() {
    MessagesTheme(mode = "system") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TransferPill(
                "Succeeded",
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer
            )
            TransferPill(
                "Failed",
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer
            )
            TransferPill(
                "Retried 3×",
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TransferLogEmptyPreview() {
    MessagesTheme(mode = "system") {
        TransferLogEmpty(Modifier.fillMaxSize())
    }
}
