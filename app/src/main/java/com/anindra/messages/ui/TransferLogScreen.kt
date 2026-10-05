package com.anindra.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anindra.messages.R
import com.anindra.messages.data.TransferEntry
import com.anindra.messages.data.TransferOperation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every import and export this device has attempted, newest first.
 *
 * A transfer that reports "restored" through a toast leaves no trace once the
 * toast is gone, which is exactly when a user asks whether their import worked.
 * This is that trace: the outcome, the reason, and what was left behind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferLogScreen(
    entries: List<TransferEntry>,
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transfer_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.icon_back))
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                stringResource(R.string.transfer_log_clear)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            TransferLogEmpty(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = SettingsLayout.SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(SettingsLayout.ROW_GAP)
        ) {
            item { Spacer(Modifier.height(SettingsLayout.TOP_GAP)) }
            // Newest first: the run a user just performed is the one they are
            // here to check, and it should not sit below twenty old ones.
            items(entries.asReversed()) { entry ->
                TransferEntryCard(entry)
            }
            item { Spacer(Modifier.height(SettingsLayout.GROUP_GAP)) }
        }
    }
}

@Composable
internal fun TransferLogEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.transfer_log_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * One run, as a card in the same shape language as the settings list.
 *
 * The outcome is carried three ways so it survives a monochrome screen or a
 * screen reader: the icon's shape and colour, and a status pill with the word.
 */
@Composable
internal fun TransferEntryCard(entry: TransferEntry) {
    val ok = entry.succeeded
    val statusContainer =
        if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val statusContent =
        if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

    GroupedRowCard(RowPosition.SINGLE) {
        Column(Modifier.padding(SettingsLayout.ROW_CONTENT_PADDING)) {
            Row(verticalAlignment = Alignment.Top) {
                TransferStatusIcon(ok)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            operationIcon(entry),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            operationLabel(entry),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        entry.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ok) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.transfer_log_meta, entry.format, entry.mode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.operation == TransferOperation.IMPORT && (entry.added > 0 || entry.skipped > 0)) {
                        Text(
                            stringResource(R.string.transfer_log_counts, entry.added, entry.skipped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    entry.conflicts.forEach { (reason, count) ->
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.transfer_log_conflict, count, conflictLabel(reason)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatTransferTime(entry.timestamp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TransferPill(
                        text = stringResource(
                            if (ok) R.string.transfer_log_status_ok else R.string.transfer_log_status_failed
                        ),
                        container = statusContainer,
                        content = statusContent
                    )
                    if (entry.attempts > 1) {
                        Spacer(Modifier.height(4.dp))
                        TransferPill(
                            text = stringResource(R.string.transfer_log_retried, entry.attempts),
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            content = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** The success/failure disc at the leading edge of a card. */
@Composable
internal fun TransferStatusIcon(ok: Boolean, modifier: Modifier = Modifier) {
    val container =
        if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content =
        if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (ok) Icons.Rounded.Check else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** A compact rounded label, used for the outcome and the retry count. */
@Composable
internal fun TransferPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/**
 * Localized name for a reason recorded by [Repository.Conflict].
 *
 * The log stores the constant, not the wording, so a run recorded in English
 * still reads correctly after the app is switched to another language.
 */
@Composable
private fun conflictLabel(reason: String): String = when (reason) {
    com.anindra.messages.data.Repository.Conflict.ALREADY_PRESENT ->
        stringResource(R.string.transfer_conflict_already_present)
    com.anindra.messages.data.Repository.Conflict.PROVIDER_ID_TAKEN ->
        stringResource(R.string.transfer_conflict_provider_id_taken)
    com.anindra.messages.data.Repository.Conflict.PROVIDER_ID_DROPPED ->
        stringResource(R.string.transfer_conflict_provider_id_dropped)
    com.anindra.messages.data.Repository.Conflict.CONVERSATION_MERGED ->
        stringResource(R.string.transfer_conflict_conversation_merged)
    com.anindra.messages.data.Repository.Conflict.CONVERSATION_UNRESOLVED ->
        stringResource(R.string.transfer_conflict_conversation_unresolved)
    com.anindra.messages.data.Repository.Conflict.RECORD_UNREADABLE ->
        stringResource(R.string.transfer_conflict_record_unreadable)
    com.anindra.messages.data.Repository.Conflict.PART_TOO_LARGE ->
        stringResource(R.string.transfer_conflict_part_too_large)
    else -> reason
}

/** Restore marks a run that startup self-healing completed, not a fresh import. */
private fun operationIcon(entry: TransferEntry): ImageVector = when {
    entry.recovered -> Icons.Rounded.Restore
    entry.operation == TransferOperation.EXPORT -> Icons.Rounded.FileUpload
    else -> Icons.Rounded.FileDownload
}

@Composable
private fun operationLabel(entry: TransferEntry): String = stringResource(
    if (entry.operation == TransferOperation.EXPORT) {
        R.string.transfer_log_export
    } else {
        R.string.transfer_log_import
    }
)

private fun formatTransferTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
