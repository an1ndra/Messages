package com.anindra.messages.ui.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.anindra.messages.ui.AlphanumericNotice
import com.anindra.messages.ui.ChatSearchBar
import com.anindra.messages.ui.ChatTopBar
import com.anindra.messages.ui.ConversationDetailsDialog
import com.anindra.messages.ui.DetailRow
import com.anindra.messages.ui.LinkWarningDialog
import com.anindra.messages.ui.MessageSelectionToolbar
import com.anindra.messages.ui.MessageRow
import com.anindra.messages.ui.ScheduledBubble
import com.anindra.messages.ui.SimPickerDialog
import com.anindra.messages.ui.SkeletonMessageRow
import com.anindra.messages.ui.TextCopyDialog
import com.anindra.messages.ui.ForwardPicker
import com.anindra.messages.ui.ImagePreview
import com.anindra.messages.ui.ProvideShimmer

@PreviewLightDark
@Preview(name = "Chat top bar", showBackground = true, widthDp = 380)
@Composable
private fun ChatTopBarPreview() {
    // The bar's states differ mostly in which trailing affordances and menu
    // entries appear, so they are previewed side by side rather than one at a
    // time: a missing item is only visible relative to the others.
    Column {
        ChatTopBar(
            convo = sampleConversation(1, "Priya Raman", "last message"),
            workProfile = false,
            sims = listOf(sampleSim()),
            currentSimId = 1,
            menuOpen = false,
            numberIsBlocked = false,
            blockingEnabled = true,
            sendCountdown = 0,
            onBack = {}, onOpenDetails = {}, onMenuToggle = {}, onMenuDismiss = {},
            onSimSelect = { _, _ -> }, onArchive = {}, onDelete = {}, onBlock = {},
            onUnblock = {}, onAddPeople = {}, onSearch = {}
        )
        ChatTopBar(
            convo = sampleConversation(2, "+15551237777", "last message", groupTitle = "Ana, Ben"),
            workProfile = true,
            sims = listOf(sampleSim(1, 0, "Jio"), sampleSim(2, 1, "Airtel")),
            currentSimId = 2,
            menuOpen = true,
            numberIsBlocked = true,
            blockingEnabled = true,
            sendCountdown = 12,
            onBack = {}, onOpenDetails = {}, onMenuToggle = {}, onMenuDismiss = {},
            onSimSelect = { _, _ -> }, onArchive = {}, onDelete = {}, onBlock = {},
            onUnblock = {}, onAddPeople = {}, onSearch = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Chat search bar", showBackground = true, widthDp = 380)
@Composable
private fun ChatSearchBarPreview() {
    Column {
        ChatSearchBar(
            query = "dinner",
            onQueryChange = {},
            matchIndex = 1,
            matchCount = 3,
            onPrevious = {},
            onNext = {},
            onClose = {}
        )
        ChatSearchBar(
            query = "",
            onQueryChange = {},
            matchIndex = -1,
            matchCount = 0,
            onPrevious = {},
            onNext = {},
            onClose = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Message selection toolbar", showBackground = true, widthDp = 380)
@Composable
private fun MessageSelectionToolbarPreview() {
    Stage {
        // Full selection: every action is available.
        MessageSelectionToolbar(
            count = 3,
            allLocked = false,
            onSelectAll = {},
            onSelectText = {},
            onSaveImage = {},
            onClose = {},
            onCopy = {},
            onForward = {},
            onShare = {},
            onViewDetails = {},
            onDelete = {},
            onLockUnlock = {}
        )
        // Locked selection: the lock action becomes unlock, and the
        // per-message actions that need a single message are dropped.
        MessageSelectionToolbar(
            count = 2,
            allLocked = true,
            onSelectAll = {},
            onSelectText = null,
            onSaveImage = null,
            onClose = {},
            onCopy = {},
            onForward = {},
            onShare = {},
            onViewDetails = {},
            onDelete = {},
            onLockUnlock = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Message row (spam & blocked)", showBackground = true, widthDp = 380)
@Composable
private fun MessageRowSpamPreview() {
    Stage {
        MessageRow(
            msg = sampleMessage(1, "Congratulations, you have won a prize", isMe = false),
            showDividerBefore = false,
            showStatus = true,
            deliveryReports = true,
            onRetry = {},
            onLongPress = {},
            highlightLinks = true
        )
        MessageRow(
            msg = sampleMessage(2, "Reply STOP to opt out", isMe = true, status = "sent"),
            showDividerBefore = true,
            showStatus = true,
            deliveryReports = true,
            onRetry = {},
            onLongPress = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Message row (locked)", showBackground = true, widthDp = 380)
@Composable
private fun MessageRowLockedPreview() {
    Stage {
        MessageRow(
            msg = sampleMessage(3, "Hidden until unlocked", isMe = false, locked = true),
            showDividerBefore = false,
            showStatus = true,
            deliveryReports = true,
            isUnlocked = false,
            onLockUnlock = {},
            onDelete = {}
        )
        MessageRow(
            msg = sampleMessage(4, "Now visible", isMe = false, locked = true),
            showDividerBefore = true,
            showStatus = true,
            deliveryReports = true,
            isUnlocked = true,
            onLockUnlock = {},
            onDelete = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Scheduled bubble", showBackground = true, widthDp = 380)
@Composable
private fun ScheduledBubblePreview() {
    Stage {
        ScheduledBubble(
            scheduled = sampleScheduled(1, "+15551230001", "See you at 6"),
            showSimIndicator = false,
            onCancel = {}
        )
        ScheduledBubble(
            scheduled = sampleScheduled(2, "+15551230002", "Reminder: pay the bill", subId = 2),
            showSimIndicator = true,
            onCancel = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Alphanumeric sender notice", showBackground = true, widthDp = 380)
@Composable
private fun AlphanumericNoticePreview() {
    Stage { AlphanumericNotice("VERIZON") }
}

@PreviewLightDark
@Preview(name = "Detail rows", showBackground = true, widthDp = 380)
@Composable
private fun DetailRowPreview() {
    Stage {
        DetailRow("Type", "SMS")
        DetailRow("To", "+15551230001")
        DetailRow("Sent", "12 Oct 2025, 09:41")
    }
}

@PreviewLightDark
@Preview(name = "Skeleton message rows", showBackground = true, widthDp = 380)
@Composable
private fun SkeletonMessageRowPreview() {
    Stage {
        ProvideShimmer {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonMessageRow()
                SkeletonMessageRow()
                SkeletonMessageRow()
            }
        }
    }
}

@PreviewLightDark
@Preview(name = "Copy text dialog", showBackground = true, widthDp = 380)
@Composable
private fun TextCopyDialogPreview() {
    Stage { TextCopyDialog("The message body that would be copied", onDismiss = {}) }
}

@PreviewLightDark
@Preview(name = "Image preview", showBackground = true, widthDp = 380, heightDp = 620)
@Composable
private fun ImagePreviewPreview() {
    // The preview renders its own opaque backdrop, so it is shown without the
    // themed Stage — a surface here would only hide it.
    ImagePreview(
        uri = "android.resource://com.anindra.messages/drawable/ic_launcher",
        onDismiss = {}
    )
}

@PreviewLightDark
@Preview(name = "Link warning dialog", showBackground = true, widthDp = 380)
@Composable
private fun LinkWarningDialogPreview() {
    Stage {
        LinkWarningDialog("http://suspicious.example.com/claim", onDismiss = {}, onOpen = {})
    }
}

@PreviewLightDark
@Preview(name = "Conversation details dialog", showBackground = true, widthDp = 380)
@Composable
private fun ConversationDetailsDialogPreview() {
    Stage {
        ConversationDetailsDialog(
            address = "+15551230001",
            name = "Priya Raman",
            messageCount = 128,
            onDismiss = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "SIM picker", showBackground = true, widthDp = 380)
@Composable
private fun SimPickerDialogPreview() {
    Stage {
        SimPickerDialog(
            sims = listOf(sampleSim(1, 0, "Jio"), sampleSim(2, 1, "Airtel")),
            currentSimId = 1,
            onSelect = {},
            onDismiss = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Forward picker", showBackground = true, widthDp = 380, heightDp = 620)
@Composable
private fun ForwardPickerPreview() {
    Stage {
        ForwardPicker(
            contacts = listOf(
                sampleContact("Priya Raman", "+15551230001"),
                sampleContact("Ana Costa", "+15551230002"),
                sampleContact("Ben Osei", "+15551230003", workProfile = true)
            ),
            conversations = listOf(
                sampleConversation(1, "Priya Raman", "Are we still on for tomorrow?"),
                sampleConversation(2, "Work group", "Ana: pushed the fix")
            ),
            onPick = { _, _ -> },
            onDismiss = {}
        )
    }
}
