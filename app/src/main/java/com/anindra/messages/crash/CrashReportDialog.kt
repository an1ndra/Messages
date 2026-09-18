package com.anindra.messages.crash

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.anindra.messages.R

@Composable
fun CrashReportDialog(
    reportCount: Int,
    onExportZip: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDelete,
        title = { Text(stringResource(R.string.crash_report_title)) },
        text = {
            Text(
                if (reportCount > 1) stringResource(R.string.crash_report_message_many, reportCount)
                else stringResource(R.string.crash_report_message)
            )
        },
        confirmButton = {
            TextButton(onClick = onExportZip) { Text(stringResource(R.string.crash_report_export)) }
        },
        dismissButton = {
            TextButton(onClick = onCopy) { Text(stringResource(R.string.crash_report_copy)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.crash_report_delete)) }
        }
    )
}
