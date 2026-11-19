package com.anindra.messages.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.anindra.messages.R
import com.anindra.messages.data.DatabaseCompatibility

/**
 * Told once, after the app has parked a database written by a newer build.
 *
 * The file is not deleted: it sits beside the live database under the name this
 * dialog gives, so reinstalling the newer build brings the messages back.
 */
@Composable
internal fun NewerDataDialog(
    notice: DatabaseCompatibility.Notice,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.db_newer_title)) },
        text = {
            Text(
                stringResource(
                    R.string.db_newer_body,
                    notice.appVersion,
                    notice.fileVersion,
                    notice.fileName
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.db_newer_ok)) }
        }
    )
}
