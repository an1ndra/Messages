package com.anindra.messages.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.anindra.messages.R

/**
 * The "share a location" dialog (#479).
 *
 * It is a text field first and a button second on purpose: the link is what
 * gets sent, and the person holding the phone can see and correct it. "Use my
 * location" fills the field from the last fix the device already had; when there
 * is none the field stays empty and the hint explains what to do instead of
 * leaving a button that appears broken.
 */
@Composable
internal fun LocationDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }

    fun fillFromDevice() {
        LocationShare.lastKnown(context)?.let { (lat, lng) ->
            text = LocationShare.geoLink(lat, lng)
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fillFromDevice() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_media_location)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.location_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    onSend(text.trim())
                    onDismiss()
                }
            ) { Text(stringResource(R.string.icon_send)) }
        },
        dismissButton = {
            TextButton(onClick = {
                if (LocationShare.hasPermission(context)) {
                    fillFromDevice()
                } else {
                    permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }) { Text(stringResource(R.string.location_use_mine)) }
        }
    )
}
