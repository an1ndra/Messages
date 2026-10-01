package com.anindra.messages.ui.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.anindra.messages.data.SwipeAction
import com.anindra.messages.ui.PersonAvatar
import com.anindra.messages.ui.ProvideShimmer
import com.anindra.messages.ui.RowPosition
import com.anindra.messages.ui.SettingsRow
import com.anindra.messages.ui.SkeletonConversationRow
import com.anindra.messages.ui.SwipeActionPreview
import com.anindra.messages.ui.SwipeDirection
import com.anindra.messages.ui.UnreadBadge
import com.anindra.messages.ui.WorkProfileBadge
import com.anindra.messages.ui.theme.A11yOptions
import com.anindra.messages.ui.theme.MessagesTheme

/**
 * Previews for the leaf components, in the debug source set so neither the
 * annotation nor the renderer reaches a release build.
 *
 * Screens are deliberately absent: every one takes an AppViewModel built on a
 * Repository, a SettingsStore and a real database, so previewing them would mean
 * maintaining a fake that drifts from the real thing. These components take
 * plain values, so what the pane draws is what ships.
 */
/** Not private: Android Studio instantiates this reflectively to expand previews. */
class ThemeModeProvider : PreviewParameterProvider<String> {
    override val values = sequenceOf("light", "dark")
}

/** Every preview renders on the real theme background, as the app does. */
@Composable
private fun Stage(
    mode: String,
    a11y: A11yOptions = A11yOptions.DISABLED,
    content: @Composable () -> Unit
) {
    MessagesTheme(mode = mode, a11y = a11y) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Preview(name = "Settings row (new UI)", showBackground = true, widthDp = 380)
@Composable
private fun SettingsRowNewUiPreview(
    @PreviewParameter(ThemeModeProvider::class) mode: String
) {
    Stage(mode) {
        SettingsRow(
            title = "Notifications",
            subtitle = "Sound and alerts",
            checked = true,
            onChecked = {},
            position = RowPosition.FIRST
        )
        SettingsRow(
            title = "Delivery reports",
            subtitle = "Find out when an SMS message is delivered",
            checked = false,
            onChecked = {},
            position = RowPosition.MIDDLE
        )
        SettingsRow(
            title = "Choose theme",
            subtitle = "System default",
            onClick = {},
            position = RowPosition.MIDDLE
        )
        SettingsRow(
            title = "Disabled row",
            subtitle = "Greyed out at 38% alpha",
            enabled = false,
            onClick = {},
            position = RowPosition.LAST
        )
    }
}

@Preview(name = "Swipe action rows (new UI)", showBackground = true, widthDp = 380)
@Composable
private fun SwipeActionRowsPreview(
    @PreviewParameter(ThemeModeProvider::class) mode: String
) {
    // The settings rows that carry a live preview of the gesture they configure.
    Stage(mode) {
        SettingsRow(
            title = "Swipe left",
            subtitle = "Mark read/unread",
            onClick = {},
            position = RowPosition.FIRST,
            preview = { SwipeActionPreview(SwipeDirection.LEFT, SwipeAction.MARK_READ_UNREAD) }
        )
        SettingsRow(
            title = "Swipe right",
            subtitle = "Delete",
            onClick = {},
            position = RowPosition.LAST,
            preview = { SwipeActionPreview(SwipeDirection.RIGHT, SwipeAction.DELETE) }
        )
    }
}

@Preview(name = "Settings row (legacy UI)", showBackground = true, widthDp = 380)
@Composable
private fun SettingsRowLegacyPreview(
    @PreviewParameter(ThemeModeProvider::class) mode: String
) {
    MessagesTheme(mode = mode) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp)
        ) {
            com.anindra.messages.ui.legacy.SettingsGroup {
                com.anindra.messages.ui.legacy.SettingsRow(
                    title = "Notifications",
                    subtitle = "Sound and alerts",
                    checked = true,
                    onChecked = {}
                )
                com.anindra.messages.ui.legacy.SettingsRow(
                    title = "Notification sound",
                    subtitle = "Default (system)",
                    onClick = {}
                )
                com.anindra.messages.ui.legacy.SettingsRow(
                    title = "Delivery reports",
                    subtitle = null,
                    checked = false,
                    onChecked = {}
                )
                com.anindra.messages.ui.legacy.SettingsRow(
                    title = "Disabled row",
                    subtitle = "Greyed out at 38% alpha",
                    enabled = false,
                    onClick = {}
                )
            }
        }
    }
}

@Preview(name = "Legacy row highlighted", showBackground = true, widthDp = 380)
@Composable
private fun LegacyRowHighlightedPreview(
    @PreviewParameter(ThemeModeProvider::class) mode: String
) {
    // What a search result flashes when the jump lands on it. The highlighted
    // title is matched by string inside SettingsRow, so it is set by name here.
    MessagesTheme(mode = mode) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.anindra.messages.ui.legacy.LocalHighlightedSetting provides "Diagnostics"
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(12.dp)
            ) {
                com.anindra.messages.ui.legacy.SettingsGroup {
                    com.anindra.messages.ui.legacy.SettingsRow(
                        title = "Redesigned interface",
                        subtitle = "Use the new settings and list layout",
                        checked = false,
                        onChecked = {}
                    )
                    com.anindra.messages.ui.legacy.SettingsRow(
                        title = "Diagnostics",
                        subtitle = "Device and app diagnostics for troubleshooting",
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Preview(name = "Swipe action previews", showBackground = true, widthDp = 380)
@Composable
private fun SwipeActionPreviewGrid(
    @PreviewParameter(ThemeModeProvider::class) mode: String
) {
    Stage(mode) {
        for (direction in SwipeDirection.entries) {
            for (action in SwipeAction.entries) {
                Column {
                    SwipeActionPreview(direction, action)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Preview(name = "Avatars and badges", showBackground = true, widthDp = 380)
@Composable
private fun AvatarsAndBadgesPreview(
    @PreviewParameter(ThemeModeProvider::class) mode: String
) {
    Stage(mode) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("+15551230001", "+15551230002", "+15551230003", "Sarah", "A").forEach {
                PersonAvatar(key = it, size = 44.dp, modifier = Modifier.padding(end = 8.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(1, 9, 99, 100).forEach {
                UnreadBadge(count = it, modifier = Modifier.padding(end = 10.dp))
            }
            WorkProfileBadge()
        }
    }
}

@Preview(name = "Avatars (large touch)", showBackground = true, widthDp = 380)
@Composable
private fun AvatarsLargeTouchPreview(
    @PreviewParameter(ThemeModeProvider::class) mode: String
) {
    Stage(mode, a11y = A11yOptions(enabled = true, largeTouchTargets = true)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(key = "+15551230001", size = 56.dp)
            Spacer(Modifier.width(12.dp))
            PersonAvatar(key = "Sarah", size = 56.dp)
        }
        com.anindra.messages.ui.legacy.SettingsGroup {
            com.anindra.messages.ui.legacy.SettingsRow(
                title = "Large touch targets",
                subtitle = "Rows grow to 72dp when accessibility mode is on",
                checked = true,
                onChecked = {}
            )
        }
    }
}

@Preview(name = "Skeleton rows", showBackground = true, widthDp = 380)
@Composable
private fun SkeletonRowsPreview(
    @PreviewParameter(ThemeModeProvider::class) mode: String
) {
    Stage(mode) {
        ProvideShimmer {
            repeat(4) { SkeletonConversationRow() }
        }
    }
}
