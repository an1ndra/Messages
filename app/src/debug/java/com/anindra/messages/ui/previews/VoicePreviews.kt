package com.anindra.messages.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.anindra.messages.ui.VoiceBubble
import com.anindra.messages.ui.theme.MessagesTheme

@PreviewLightDark
@Composable
private fun IncomingVoiceBubblePreview() {
    MessagesTheme(mode = "system") {
        VoiceBubble(uri = "content://preview/voice-incoming", isMe = false)
    }
}

@PreviewLightDark
@Composable
private fun OutgoingVoiceBubbleWithCaptionPreview() {
    MessagesTheme(mode = "system") {
        VoiceBubble(
            uri = "content://preview/voice-outgoing",
            isMe = true,
            body = androidx.compose.ui.text.AnnotatedString("Listen to this"),
        )
    }
}
