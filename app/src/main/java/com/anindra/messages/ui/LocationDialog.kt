package com.anindra.messages.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.anindra.messages.R

/**
 * Confirms sharing where the device is: the message that leaves the app is a
 * maps link anyone in the chat can open, so it asks first.
 */
@Composable
internal fun LocationDialog(
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_location_title)) },
        text = { Text(stringResource(R.string.chat_location_body)) },
        confirmButton = {
            TextButton(onClick = onSend) { Text(stringResource(R.string.chat_location_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        }
    )
}
