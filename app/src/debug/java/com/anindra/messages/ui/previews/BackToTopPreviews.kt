package com.anindra.messages.ui.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.anindra.messages.ui.BackToTopFab
import com.anindra.messages.ui.theme.MessagesTheme

@PreviewLightDark
@Composable
private fun BackToTopFabPreview() {
    MessagesTheme(mode = "system") {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Scrolled far enough for the shortcut")
            BackToTopFab(onClick = {})
        }
    }
}
