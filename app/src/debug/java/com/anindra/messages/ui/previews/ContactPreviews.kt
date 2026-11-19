package com.anindra.messages.ui.previews

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.anindra.messages.ui.ContactBubble
import com.anindra.messages.ui.ContactCardBubble
import com.anindra.messages.ui.theme.MessagesTheme

@PreviewLightDark
@Composable
private fun IncomingContactCardPreview() {
    MessagesTheme(mode = "system") {
        Column(Modifier.width(300.dp).padding(12.dp)) {
            ContactCardBubble(name = "Alex Rivera", number = "+1 555 333 4444", isMe = false)
        }
    }
}

@PreviewLightDark
@Composable
private fun OutgoingContactCardPreview() {
    MessagesTheme(mode = "system") {
        Column(Modifier.width(300.dp).padding(12.dp)) {
            ContactCardBubble(name = "Priya Raman", number = "+1 555 775 010", isMe = true)
        }
    }
}

@PreviewLightDark
@Composable
private fun ContactBubblePreview() {
    MessagesTheme(mode = "system") {
        Column(Modifier.width(300.dp).padding(12.dp)) {
            ContactBubble(uri = "content://preview/contact", isMe = false)
        }
    }
}
