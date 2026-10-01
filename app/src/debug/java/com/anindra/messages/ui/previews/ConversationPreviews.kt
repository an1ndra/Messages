package com.anindra.messages.ui.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.anindra.messages.data.SwipeAction
import com.anindra.messages.ui.ChangeAffordance
import com.anindra.messages.ui.ConversationRow
import com.anindra.messages.ui.ExpressiveTab
import com.anindra.messages.ui.ExpressiveTabs
import com.anindra.messages.ui.SheetActionRow
import com.anindra.messages.ui.StartChatFab
import com.anindra.messages.ui.SwipeActionDialog
import com.anindra.messages.ui.SwipeActionPreview
import com.anindra.messages.ui.SwipeConversationItem
import com.anindra.messages.ui.SwipeDirection
import com.anindra.messages.ui.theme.A11yOptions
import com.anindra.messages.ui.theme.MessagesTheme
import com.anindra.messages.R

/** The long-press action sheet, in both the destructive and the neutral tone. */
@PreviewLightDark
@Preview(name = "Conversation action sheet rows", showBackground = true, widthDp = 380)
@Composable
private fun SheetActionRowsPreview() {
    Stage {
        SheetActionRow(Icons.Rounded.PushPin, "Pin", MaterialTheme.colorScheme.onSurface, {})
        SheetActionRow(Icons.Rounded.MarkChatRead, "Mark as read", MaterialTheme.colorScheme.onSurface, {})
        SheetActionRow(Icons.Rounded.Drafts, "Mark as unread", MaterialTheme.colorScheme.onSurface, {})
        SheetActionRow(Icons.Rounded.Archive, "Archive", MaterialTheme.colorScheme.onSurface, {})
        SheetActionRow(Icons.Rounded.Block, "Block", MaterialTheme.colorScheme.error, {})
        SheetActionRow(Icons.Rounded.Delete, "Delete", MaterialTheme.colorScheme.error, {})
    }
}

@PreviewLightDark
@Preview(name = "New chat FAB", showBackground = true, widthDp = 380)
@Composable
private fun StartChatFabPreview() {
    Stage {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StartChatFab(expanded = false) {}
            StartChatFab(expanded = true) {}
        }
    }
}

@PreviewLightDark
@Preview(name = "Conversation rows", showBackground = true, widthDp = 380)
@Composable
private fun ConversationRowsPreview() {
    Stage {
        ConversationRow(
            context = androidx.compose.ui.platform.LocalContext.current,
            settings = fullRowSettings,
            convo = sampleConversation(1, "Priya Raman", "Are we still on for tomorrow?", unreadCount = 2),
            workProfile = false,
            showArchived = false,
            onClick = {}
        )
        ConversationRow(
            context = androidx.compose.ui.platform.LocalContext.current,
            settings = fullRowSettings,
            convo = sampleConversation(2, "+15551239999", "Ok sounds good 👍", isMe = true),
            workProfile = false,
            showArchived = false,
            onClick = {}
        )
        ConversationRow(
            context = androidx.compose.ui.platform.LocalContext.current,
            settings = fullRowSettings,
            convo = sampleConversation(3, "Work group", "Ana: pushed the fix", groupTitle = "Ana, Ben, Chi"),
            workProfile = true,
            showArchived = false,
            onClick = {}
        )
        ConversationRow(
            context = androidx.compose.ui.platform.LocalContext.current,
            settings = fullRowSettings,
            convo = sampleConversation(4, "Draft thread", "last message", draft = "typing a reply"),
            workProfile = false,
            showArchived = false,
            onClick = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Conversation row (archived, pinned, blocked)", showBackground = true, widthDp = 380)
@Composable
private fun ConversationRowFlagsPreview() {
    // The flag badges share a row, so they are previewed together rather than
    // one at a time; an overlap only shows up when they coexist.
    Stage {
        ConversationRow(
            context = androidx.compose.ui.platform.LocalContext.current,
            settings = fullRowSettings,
            convo = sampleConversation(5, "Pinned thread", "stuck to the top", pinned = true),
            workProfile = false,
            showArchived = false,
            onClick = {}
        )
        ConversationRow(
            context = androidx.compose.ui.platform.LocalContext.current,
            settings = fullRowSettings,
            convo = sampleConversation(6, "Archived thread", "moved out of the inbox", archived = true),
            workProfile = false,
            showArchived = true,
            onClick = {}
        )
        ConversationRow(
            context = androidx.compose.ui.platform.LocalContext.current,
            settings = fullRowSettings,
            convo = sampleConversation(7, "Spam sender", "WIN A PRIZE", blocked = true, unreadCount = 9),
            workProfile = false,
            showArchived = false,
            onClick = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Swipe action dialog", showBackground = true, widthDp = 380)
@Composable
private fun SwipeActionDialogPreview() {
    Stage {
        SwipeActionDialog(current = SwipeAction.DELETE, onPick = {}, onDismiss = {})
    }
}

@PreviewLightDark
@Preview(name = "Change affordance label", showBackground = true, widthDp = 380)
@Composable
private fun ChangeAffordancePreview() {
    // The section heading above the swipe action pickers.
    Stage { ChangeAffordance() }
}

@PreviewLightDark
@Preview(name = "Connected tabs", showBackground = true, widthDp = 380)
@Composable
private fun ExpressiveTabsPreview() {
    Stage {
        ExpressiveTabs(
            tabs = listOf(
                ExpressiveTab(R.string.trash_tab_conversations, Icons.Rounded.ChatBubble),
                ExpressiveTab(R.string.trash_tab_messages, Icons.AutoMirrored.Rounded.Chat)
            ),
            selected = 0,
            onSelect = {}
        )
        ExpressiveTabs(
            tabs = listOf(
                ExpressiveTab(R.string.spam_tab_conversations, Icons.Rounded.ChatBubble),
                ExpressiveTab(R.string.spam_tab_messages, Icons.AutoMirrored.Rounded.Chat)
            ),
            selected = 1,
            onSelect = {}
        )
    }
}
