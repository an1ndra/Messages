package com.anindra.messages.ui.previews

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.anindra.messages.ui.AboutScreen
import com.anindra.messages.ui.Identity
import com.anindra.messages.ui.theme.MessagesTheme

@PreviewLightDark
@Composable
private fun AboutScreenPreview() {
    MessagesTheme(mode = "system") {
        AboutScreen(
            requestDiagnostics = { onReady -> onReady("Messages diagnostics report\n--- App ---") },
            onBack = {},
            scrollState = rememberScrollState()
        )
    }
}

@PreviewLightDark
@Composable
private fun AboutIdentityPreview() {
    MessagesTheme(mode = "system") {
        Identity(versionName = "1.0.31", versionCode = 31)
    }
}
