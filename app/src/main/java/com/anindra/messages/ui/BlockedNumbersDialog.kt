package com.anindra.messages.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anindra.messages.R
import com.anindra.messages.data.BlockedNumber

/**
 * Every blocked number, reachable (#405 follow-up).
 *
 * The count in the settings row and the rows here are the same list, so a
 * dialog that only painted the first screenful read as a bug in the count: a
 * reporter with 164 blocked senders saw 14 and no page after them. A dialog
 * whose text slot is a plain `Column` is clipped by its finite height, and the
 * rows past that point get zero height rather than a scrollbar. The list
 * therefore scrolls inside a bounded height.
 */
@Composable
internal fun BlockedNumbersDialog(
    blockedNumbers: List<BlockedNumber>,
    onUnblock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_blocked_numbers_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (blockedNumbers.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_blocked_numbers_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    blockedNumbers.forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                formatPhoneNumber(entry.number),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onUnblock(entry.number) }) {
                                Text(stringResource(R.string.common_unblock))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}
