package com.anindra.messages.ui.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.anindra.messages.data.Message
import com.anindra.messages.data.SimCard
import com.anindra.messages.data.SwipeAction
import com.anindra.messages.ui.AttachSheet
import com.anindra.messages.ui.BubblePosition
import com.anindra.messages.ui.ChatBubble
import com.anindra.messages.ui.InputBar
import com.anindra.messages.ui.MessageDetailsDialog
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
/**
 * Every preview renders on the real theme background, as the app does.
 *
 * [mode] is left on "system" so the theme follows the preview's `uiMode`: that
 * is what lets `@PreviewLightDark` switch the whole set, rather than each
 * preview having to pass a mode through a parameter.
 */
@Composable
internal fun Stage(
    a11y: A11yOptions = A11yOptions.DISABLED,
    content: @Composable () -> Unit
) {
    MessagesTheme(mode = "system", a11y = a11y) {
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

@PreviewLightDark
@Preview(name = "Settings row (new UI)", showBackground = true, widthDp = 380)
@Composable
private fun SettingsRowNewUiPreview() {
    Stage() {
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

@PreviewLightDark
@Preview(name = "Swipe action rows (new UI)", showBackground = true, widthDp = 380)
@Composable
private fun SwipeActionRowsPreview() {
    // The settings rows that carry a live preview of the gesture they configure.
    Stage() {
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

@PreviewLightDark
@Preview(name = "Settings row (legacy UI)", showBackground = true, widthDp = 380)
@Composable
private fun SettingsRowLegacyPreview() {
    MessagesTheme(mode = "system") {
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

@PreviewLightDark
@Preview(name = "Legacy row highlighted", showBackground = true, widthDp = 380)
@Composable
private fun LegacyRowHighlightedPreview() {
    // What a search result flashes when the jump lands on it. The highlighted
    // title is matched by string inside SettingsRow, so it is set by name here.
    MessagesTheme(mode = "system") {
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

@PreviewLightDark
@Preview(name = "Swipe action previews", showBackground = true, widthDp = 380)
@Composable
private fun SwipeActionPreviewGrid() {
    Stage() {
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

@PreviewLightDark
@Preview(name = "Avatars and badges", showBackground = true, widthDp = 380)
@Composable
private fun AvatarsAndBadgesPreview() {
    Stage() {
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

@PreviewLightDark
@Preview(name = "Avatars (large touch)", showBackground = true, widthDp = 380)
@Composable
private fun AvatarsLargeTouchPreview() {
    Stage(a11y = A11yOptions(enabled = true, largeTouchTargets = true)) {
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

/**
 * Font scale is previewed through the app's own accessibility setting rather than
 * `@PreviewFontScales`, because that is the multiplier that actually ships: the
 * theme composes a Density from `A11yOptions.fontScalePercent` on top of the
 * system scale. The extremes come from `PERCENT_OPTIONS`, so a value outside that
 * range cannot be selected in the app and is not worth rendering.
 */
@PreviewLightDark
@Preview(name = "Font scale 130% (largest)", showBackground = true, widthDp = 380)
@Composable
private fun FontScaleLargestPreview() {
    Stage(a11y = A11yOptions(enabled = true, fontScalePercent = 130)) {
        com.anindra.messages.ui.legacy.SettingsGroup {
            com.anindra.messages.ui.legacy.SettingsRow(
                title = "Notifications",
                subtitle = "Sound, vibration and alerts",
                checked = true,
                onChecked = {}
            )
            com.anindra.messages.ui.legacy.SettingsRow(
                title = "Message font size",
                subtitle = "130% of the system size",
                onClick = {}
            )
        }
        ChatBubble(
            msg = bubble("Does this still fit on one line at 130%?", isMe = false, id = 500L),
            showTime = true,
            onTap = {},
            deliveryReports = true,
            highlightLinks = true,
            linkWarningEnabled = false,
            hideLinks = false,
            isUnlocked = true,
            showSimIndicator = false
        )
    }
}

@PreviewLightDark
@Preview(name = "Font scale 85% (smallest)", showBackground = true, widthDp = 380)
@Composable
private fun FontScaleSmallestPreview() {
    Stage(a11y = A11yOptions(enabled = true, fontScalePercent = 85)) {
        ChatBubble(
            msg = bubble("And at 85%?", isMe = true, id = 501L),
            showTime = true,
            onTap = {},
            deliveryReports = true,
            highlightLinks = true,
            linkWarningEnabled = false,
            hideLinks = false,
            isUnlocked = true,
            showSimIndicator = false
        )
    }
}

/**
 * A bubble is previewed in its resting state. The entrance animation is a
 * one-shot `Animatable` that resolves to progress 1f on the first frame when
 * `animateIn` is false, so the preview shows the settled bubble rather than a
 * half-finished transform.
 */
private fun bubble(
    body: String,
    isMe: Boolean,
    status: String = "sent",
    subId: Int = -1,
    locked: Boolean = false,
    mediaType: String = "text",
    reactions: Map<String, Int> = emptyMap(),
    id: Long = 1L
) = Message(
    id = id,
    conversationId = 1L,
    body = body,
    timestamp = 1_757_000_000_000L,
    isMe = isMe,
    status = status,
    mediaType = mediaType,
    reactions = reactions,
    locked = locked,
    subId = subId
)

@Composable
internal fun BubbleStage(
    a11y: A11yOptions = A11yOptions.DISABLED,
    content: @Composable () -> Unit
) {
    MessagesTheme(mode = "system", a11y = a11y) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@PreviewLightDark
@Preview(name = "Chat bubbles", showBackground = true, widthDp = 380)
@Composable
private fun ChatBubblesPreview() {
    BubbleStage() {
        ChatBubble(
            msg = bubble("Are we still on for tomorrow?", isMe = false),
            showTime = true,
            onTap = {},
            deliveryReports = true,
            highlightLinks = true,
            linkWarningEnabled = false,
            hideLinks = false,
            isUnlocked = true,
            showSimIndicator = false
        )
        ChatBubble(
            msg = bubble("Yes. I'll bring the tickets.", isMe = true, id = 2L),
            showTime = true,
            onTap = {},
            deliveryReports = true,
            highlightLinks = true,
            linkWarningEnabled = false,
            hideLinks = false,
            isUnlocked = true,
            showSimIndicator = false
        )
        ChatBubble(
            msg = bubble("Delivered", isMe = true, status = "delivered", id = 3L),
            showTime = true,
            onTap = {},
            deliveryReports = true,
            highlightLinks = true,
            linkWarningEnabled = false,
            hideLinks = false,
            isUnlocked = true,
            showSimIndicator = false
        )
    }
}

@PreviewLightDark
@Preview(name = "Bubble statuses", showBackground = true, widthDp = 380)
@Composable
private fun BubbleStatusesPreview() {
    // The meta line under a bubble, which is the part most likely to regress:
    // transport label, delivery report, failure with its retry affordance, and
    // the SIM slot suffix for a dual-SIM send.
    BubbleStage() {
        listOf("sending" to "Sending…", "sent" to "SMS", "delivered" to "Delivered").forEach {
            (status, body) ->
            ChatBubble(
                msg = bubble(body, isMe = true, status = status, id = status.hashCode().toLong()),
                showTime = true,
                onTap = {},
                deliveryReports = true,
                highlightLinks = true,
                linkWarningEnabled = false,
                hideLinks = false,
                isUnlocked = true,
                showSimIndicator = true,
                position = BubblePosition.MIDDLE
            )
        }
        ChatBubble(
            msg = bubble("This one did not go out", isMe = true, status = "failed", id = 99L),
            showTime = true,
            onTap = {},
            deliveryReports = true,
            highlightLinks = true,
            linkWarningEnabled = false,
            hideLinks = false,
            isUnlocked = true,
            showSimIndicator = false,
            position = BubblePosition.LAST,
            onRetry = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Bubble grouping and reactions", showBackground = true, widthDp = 380)
@Composable
private fun BubbleGroupingPreview() {
    // BubblePosition drives which corners soften, so a run of same-sender
    // messages reads as one group. Reactions and the locked placeholder round
    // out the variants a conversation can actually show.
    BubbleStage() {
        var id = 100L
        listOf(
            BubblePosition.FIRST,
            BubblePosition.MIDDLE,
            BubblePosition.MIDDLE,
            BubblePosition.LAST
        ).forEach { position ->
            ChatBubble(
                msg = bubble("Grouped message ${position.name.lowercase()}", isMe = true, id = id++),
                showTime = position == BubblePosition.LAST,
                onTap = {},
                deliveryReports = true,
                highlightLinks = true,
                linkWarningEnabled = false,
                hideLinks = false,
                isUnlocked = true,
                showSimIndicator = false,
                position = position
            )
        }
        ChatBubble(
            msg = bubble("Thanks! 🎉", isMe = false, reactions = mapOf("👍" to 2, "❤️" to 1), id = 200L),
            showTime = true,
            senderName = "Priya",
            onTap = {},
            deliveryReports = true,
            highlightLinks = true,
            linkWarningEnabled = false,
            hideLinks = false,
            isUnlocked = true,
            showSimIndicator = false
        )
        ChatBubble(
            msg = bubble("Secret", isMe = false, locked = true, id = 300L),
            showTime = false,
            onTap = {},
            deliveryReports = true,
            highlightLinks = true,
            linkWarningEnabled = false,
            hideLinks = false,
            isUnlocked = false,
            showSimIndicator = false
        )
    }
}

@PreviewLightDark
@Preview(name = "Bubble (large touch)", showBackground = true, widthDp = 380)
@Composable
private fun BubbleLargeTouchPreview() {
    BubbleStage(a11y = A11yOptions(enabled = true, largeTouchTargets = true)) {
        ChatBubble(
            msg = bubble("Accessibility mode on", isMe = true),
            showTime = true,
            onTap = {},
            deliveryReports = true,
            highlightLinks = true,
            linkWarningEnabled = false,
            hideLinks = false,
            isUnlocked = true,
            showSimIndicator = false
        )
    }
}

@PreviewLightDark
@Preview(name = "Message details", showBackground = true, widthDp = 380)
@Composable
private fun MessageDetailsPreview() {
    MessagesTheme(mode = "system") {
        MessageDetailsDialog(
            message = bubble(
                "Here is the tracking link you asked for",
                isMe = false,
                id = 400L
            ).copy(address = "+15551230001", transport = "mms", deliveredAt = 1_757_000_060_000L),
            address = "+15551230001",
            onDismiss = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Compose input bar", showBackground = true, widthDp = 380)
@Composable
private fun InputBarPreview() {
    MessagesTheme(mode = "system") {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            InputBar(
                draft = "Running ten minutes late",
                placeholder = "Text message",
                onDraftChange = {},
                onSend = {},
                onEmojiToggle = {},
                onAttach = {},
                showEmojiButton = true,
                sims = listOf(
                    SimCard(
                        subscriptionId = 1,
                        slotIndex = 0,
                        carrierName = "Jio",
                        displayName = "Jio",
                        mccMnc = "40445",
                        countryIso = "IN",
                        embedded = false
                    )
                ),
                currentSimId = 1
            )
        }
    }
}

@PreviewLightDark
@Preview(name = "Attach sheet", showBackground = true, widthDp = 380, heightDp = 640)
@Composable
private fun AttachSheetPreview() {
    MessagesTheme(mode = "system") {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AttachSheet(onGallery = {}, onCamera = {}, onDismiss = {})
        }
    }
}

@PreviewLightDark
@Preview(name = "Skeleton rows", showBackground = true, widthDp = 380)
@Composable
private fun SkeletonRowsPreview() {
    Stage() {
        ProvideShimmer {
            repeat(4) { SkeletonConversationRow() }
        }
    }
}
