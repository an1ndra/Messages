package com.anindra.messages.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.anindra.messages.R

@Composable
fun ArchiveInboundChoiceDialog(
    onBringToInbox: () -> Unit,
    onKeepArchived: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onBringToInbox,
        modifier = modifier,
        title = { Text(stringResource(R.string.archive_inbound_choice_title)) },
        text = { Text(stringResource(R.string.archive_inbound_choice_body)) },
        confirmButton = {
            Button(onClick = onBringToInbox) {
                Text(stringResource(R.string.archive_inbound_choice_inbox))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepArchived) {
                Text(stringResource(R.string.archive_inbound_choice_archive))
            }
        }
    )
}
