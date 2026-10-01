package com.anindra.messages.ui.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.anindra.messages.data.SwipeAction
import com.anindra.messages.ui.ChatMessageList
import com.anindra.messages.ui.ConversationsTab
import com.anindra.messages.ui.EmptyTrash
import com.anindra.messages.ui.GroupedRowList
import com.anindra.messages.ui.ImageBubble
import com.anindra.messages.ui.MmsSupportScreen
import com.anindra.messages.ui.MessagesTab
import com.anindra.messages.ui.ProvideShimmer
import com.anindra.messages.ui.RowPosition
import com.anindra.messages.ui.SettingsFooter
import com.anindra.messages.ui.SettingsGroup
import com.anindra.messages.ui.SettingsRow
import com.anindra.messages.ui.SkeletonMessageRow
import com.anindra.messages.ui.SwipeActionPreview
import com.anindra.messages.ui.SwipeConversationItem
import com.anindra.messages.ui.SwipeDirection
import com.anindra.messages.ui.legacy.SettingsCard
import com.anindra.messages.ui.legacy.SettingsGroup as LegacySettingsGroup

@PreviewLightDark
@Preview(name = "Settings group (new UI)", showBackground = true, widthDp = 380)
@Composable
private fun SettingsGroupPreview() {
    // The new UI separates the gap from the rounding, so the two containers are
    // previewed separately: SettingsGroup owns the gap, GroupedRowList the card.
    Stage {
        SettingsGroup {
            SettingsRow(title = "Notifications", position = RowPosition.FIRST, onClick = {})
            SettingsRow(title = "Notification sound", position = RowPosition.LAST, onClick = {})
        }
        GroupedRowList {
            SettingsRow(title = "Linked devices", position = RowPosition.FIRST, onClick = {})
            SettingsRow(title = "Storage", position = RowPosition.MIDDLE, onClick = {})
            SettingsRow(title = "Help", position = RowPosition.LAST, onClick = {})
        }
    }
}

@PreviewLightDark
@Preview(name = "Settings group (legacy UI)", showBackground = true, widthDp = 380)
@Composable
private fun LegacySettingsGroupPreview() {
    Stage {
        LegacySettingsGroup {
            com.anindra.messages.ui.legacy.SettingsRow(
                title = "Notifications",
                subtitle = "Sound and alerts",
                onClick = {}
            )
            com.anindra.messages.ui.legacy.SettingsRow(
                title = "Notification sound",
                subtitle = "Default (system)",
                onClick = {}
            )
        }
    }
}

@PreviewLightDark
@Preview(name = "Legacy search card wrapper", showBackground = true, widthDp = 380)
@Composable
private fun SettingsCardPreview() {
    // SettingsCard is what records a card's offset for the search jump, so it
    // is previewed with the same indices the search index assigns.
    Stage {
        SettingsCard(card = 0) {
            com.anindra.messages.ui.legacy.SettingsRow(
                title = "Redesigned interface",
                subtitle = "Use the new settings and list layout",
                checked = false,
                onChecked = {}
            )
        }
        SettingsCard(card = 7) {
            com.anindra.messages.ui.legacy.SettingsRow(
                title = "MMS support check",
                subtitle = "What your carrier reports",
                onClick = {}
            )
        }
    }
}

@PreviewLightDark
@Preview(name = "Chat message list", showBackground = true, widthDp = 380, heightDp = 620)
@Composable
private fun ChatMessageListPreview() {
    Stage {
        ChatMessageList(
            messages = listOf(
                sampleMessage(1, "Are we still on for tomorrow?", isMe = false, timestamp = SAMPLE_TS_OLD),
                sampleMessage(2, "Yes. I'll bring the tickets.", isMe = true, timestamp = SAMPLE_TS_OLD + 60_000),
                sampleMessage(3, "Delivered", isMe = true, status = "delivered", timestamp = SAMPLE_TS_OLD + 120_000),
                sampleMessage(4, "This one did not go out", isMe = true, status = "failed", timestamp = SAMPLE_TS)
            ),
            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
            deliveryReports = true,
            sims = listOf(sampleSim()),
            highlightLinks = true,
            linkWarningEnabled = false,
            hideLinks = false,
            forwardingEnabled = true,
            unlockedIds = emptySet(),
            showEntrySkeleton = false,
            pendingEarlier = false,
            hasEarlierButton = true,
            onLoadEarlier = {},
            onRetry = {},
            onRetryWithPicker = { _ -> },
            onLongPress = {},
            onLockUnlock = { _, _ -> },
            onDeleteMessage = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Chat message list (loading skeleton)", showBackground = true, widthDp = 380, heightDp = 620)
@Composable
private fun ChatMessageListSkeletonPreview() {
    Stage {
        ChatMessageList(
            messages = emptyList(),
            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
            deliveryReports = true,
            sims = listOf(sampleSim()),
            highlightLinks = true,
            linkWarningEnabled = false,
            hideLinks = false,
            forwardingEnabled = true,
            unlockedIds = emptySet(),
            showEntrySkeleton = true,
            pendingEarlier = true,
            hasEarlierButton = false,
            onLoadEarlier = {},
            onRetry = {},
            onRetryWithPicker = { _ -> },
            onLongPress = {},
            onLockUnlock = { _, _ -> },
            onDeleteMessage = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Image bubble", showBackground = true, widthDp = 380)
@Composable
private fun ImageBubblePreview() {
    // A content:// URI with no provider behind it: the point is the bubble's
    // shape, caption layout and corner handling, not the decoded bitmap, which
    // a preview has no file access to load anyway.
    Stage {
        ImageBubble(uri = "", isMe = false)
        ImageBubble(uri = "", isMe = true)
    }
}

@PreviewLightDark
@Preview(name = "Spam: conversations tab", showBackground = true, widthDp = 380, heightDp = 560)
@Composable
private fun SpamConversationsTabPreview() {
    Stage {
        ConversationsTab(
            blocked = listOf(
                sampleConversation(1, "WINNER", "You have won a prize", blocked = true, unreadCount = 4),
                sampleConversation(2, "Offers", "50% off today only", blocked = true)
            ),
            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
            onOpenConversation = {},
            onUnblock = {},
            onDelete = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Spam: messages tab", showBackground = true, widthDp = 380, heightDp = 560)
@Composable
private fun SpamMessagesTabPreview() {
    Stage {
        MessagesTab(
            messages = listOf(
                sampleBlockedMessage(1, "WINNER", "Claim your prize now"),
                sampleBlockedMessage(2, "Offers", "Today only", reason = "Blocked number")
            ),
            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
            restorable = { true },
            onDelete = {},
            onRestore = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Trash empty state", showBackground = true, widthDp = 380, heightDp = 400)
@Composable
private fun EmptyTrashPreview() {
    Stage {
        EmptyTrash(
            title = "Trash is empty",
            message = "Deleted messages and conversations stay here for 30 days."
        )
    }
}

@PreviewLightDark
@Preview(name = "MMS support check", showBackground = true, widthDp = 380, heightDp = 560)
@Composable
private fun MmsSupportPreview() {
    // Reads the real carrier config, which a preview cannot reach, so this
    // renders the no-SIMs state. That state is worth pinning: it is what a
    // device with no SIMs shows, and it is easy to break the branch on.
    Stage {
        MmsSupportScreen(onBack = {})
    }
}
