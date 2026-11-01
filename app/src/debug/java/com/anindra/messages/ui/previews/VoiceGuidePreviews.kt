package com.anindra.messages.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import com.anindra.messages.ui.GuideRow
import com.anindra.messages.ui.VoiceGuideCard
import com.anindra.messages.ui.theme.MessagesTheme

@PreviewLightDark
@Composable
private fun VoiceGuideCardPreview() {
    MessagesTheme(mode = "system") {
        VoiceGuideCard(onDismiss = {})
    }
}

@PreviewLightDark
@Composable
private fun VoiceGuideRowPreview() {
    MessagesTheme(mode = "system") {
        GuideRow(
            icon = {
                Icon(
                    Icons.Rounded.ChevronLeft, null,
                    modifier = androidx.compose.ui.Modifier.size(20.dp)
                )
            },
            text = "Swipe up to lock it and record hands-free"
        )
    }
}
