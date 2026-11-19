package com.anindra.messages.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.anindra.messages.R

/**
 * What a tapped contact card can do: keep it in the contacts app, or save the
 * file so it can be opened later.
 *
 * A card sent over MMS is only useful if the phone can save it — the receiving
 * app decides how it arrives, but what happens next is this dialog.
 */
@Composable
internal fun ContactCardDialog(
    uri: String,
    onAddToContacts: (name: String?, number: String?) -> Unit,
    onSaveFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val card = remember(uri) { ContactShare.readCard(context, Uri.parse(uri)) }
    val name = card?.first
    val number = card?.second

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    text = name ?: stringResource(R.string.chat_media_contact),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (number != null) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { onAddToContacts(name, number) },
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.chat_add_to_contacts)) }
                TextButton(
                    onClick = onSaveFile,
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.chat_save_contact)) }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}
