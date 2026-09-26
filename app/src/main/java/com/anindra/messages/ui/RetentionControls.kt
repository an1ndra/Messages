package com.anindra.messages.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anindra.messages.R
import com.anindra.messages.data.RetentionPolicy

/**
 * The "how long do you keep things" control for Trash and Spam & Blocked, as an
 * app-bar action so it does not push the folder list down. Whether auto-delete
 * runs at all, and which buckets it covers, lives in Advanced; this is only the
 * span, so the answer sits with the folder it applies to.
 */
@Composable
fun AutoDeleteDurationAction(
    days: Int,
    active: Boolean,
    onPick: (Int) -> Unit
) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(false) }
    val label = if (active) {
        context.getString(R.string.settings_retention_action_active, days)
    } else {
        stringResource(R.string.settings_retention_action_off)
    }

    IconButton(onClick = { dialog = true }) {
        Icon(
            imageVector = Icons.Rounded.AutoDelete,
            contentDescription = label,
            tint = if (active) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                IconTint.disabled(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    isSystemInDarkTheme()
                )
            }
        )
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.settings_retention_after)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_choose_retention),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    RetentionPolicy.DAY_OPTIONS.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(option)
                                    dialog = false
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = days == option,
                                onClick = {
                                    onPick(option)
                                    dialog = false
                                },
                                modifier = Modifier.size(40.dp)
                            )
                            Text(context.getString(R.string.settings_retention_days, option))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
