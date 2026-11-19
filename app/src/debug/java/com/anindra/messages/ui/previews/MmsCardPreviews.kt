package com.anindra.messages.ui.previews

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.anindra.messages.ui.GroupedRowList
import com.anindra.messages.ui.MmsCardRow
import com.anindra.messages.ui.theme.MessagesTheme

@PreviewLightDark
@Composable
private fun MmsCardRowPreview() {
    MessagesTheme(mode = "system") {
        Column(Modifier.padding(16.dp)) {
            GroupedRowList {
                MmsCardRow(
                    title = "MMS",
                    subtitle = "Pictures, voice notes and contacts are sent over MMS",
                    trailing = { Switch(checked = true, onCheckedChange = {}) },
                    showDivider = true
                )
                MmsCardRow(
                    title = "Android",
                    subtitle = "Off on this carrier · +1 · US · +15555215556",
                    showDivider = false
                )
            }
        }
    }
}
