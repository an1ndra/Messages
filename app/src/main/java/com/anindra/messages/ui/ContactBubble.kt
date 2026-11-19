package com.anindra.messages.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anindra.messages.R
import com.anindra.messages.ui.theme.incomingBubble
import com.anindra.messages.ui.theme.outgoingBubble
import com.anindra.messages.ui.theme.selectedBubble

/**
 * A contact card in the conversation: the name and number the card carries, not
 * the properties it is made of.
 *
 * The card is read back from the attachment itself rather than kept in the
 * message body, so it stays right after the provider holds the part and the
 * original cache file is gone.
 */
@Composable
internal fun ContactBubble(
    uri: String,
    isMe: Boolean,
    isSelected: Boolean = false,
    onLongPress: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val card = remember(uri) { ContactShare.readCard(context, Uri.parse(uri)) }
    ContactCardBubble(
        name = card?.first,
        number = card?.second,
        isMe = isMe,
        isSelected = isSelected,
        onLongPress = onLongPress,
        onClick = onClick
    )
}

/** The card itself, without the attachment behind it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactCardBubble(
    name: String?,
    number: String?,
    isMe: Boolean,
    isSelected: Boolean = false,
    onLongPress: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val label = stringResource(R.string.chat_media_contact)

    Surface(
        color = if (isSelected) cs.selectedBubble else if (isMe) cs.outgoingBubble else cs.incomingBubble,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .widthIn(max = 260.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Person,
                contentDescription = label,
                tint = cs.onPrimaryContainer,
                modifier = Modifier
                    .size(36.dp)
                    .background(cs.primaryContainer, CircleShape)
                    .padding(6.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = name ?: label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isMe) cs.onPrimaryContainer else cs.onSurface
                )
                if (number != null) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isMe) cs.onPrimaryContainer else cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}
