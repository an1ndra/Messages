package com.anindra.messages.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anindra.messages.R

@Composable
fun DiagnosticsDialog(
    report: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diagnostics_title)) },
        text = {
            Text(
                report,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.diagnostics_close)) }
                TextButton(onClick = onCopy) { Text(stringResource(R.string.diagnostics_copy)) }
            }
        }
    )
}
