package com.anindra.messages.ui.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.anindra.messages.ui.ChatSchedulePicker
import com.anindra.messages.ui.ConversationRow
import com.anindra.messages.ui.ImportRadioGroup
import com.anindra.messages.ui.ScheduledRow
import com.anindra.messages.ui.SheetActionRow
import com.anindra.messages.ui.StartChatFab
import com.anindra.messages.ui.SwipeConversationItem
import com.anindra.messages.ui.legacy.ConversationRow as LegacyConversationRow
import com.anindra.messages.ui.legacy.ImportRadioGroup as LegacyImportRadioGroup
import com.anindra.messages.ui.legacy.SheetActionRow as LegacySheetActionRow
import com.anindra.messages.ui.legacy.StartChatFab as LegacyStartChatFab
import com.anindra.messages.ui.legacy.SwipeConversationItem as LegacySwipeConversationItem

/**
 * The legacy list, which is the default UI, previewed alongside the redesigned
 * one. The two are separate implementations rather than one behind a flag, so a
 * change to one is invisible in the other's preview; showing both is the only
 * way to notice they have drifted apart.
 */
@PreviewLightDark
@Preview(name = "Legacy conversation rows", showBackground = true, widthDp = 380)
@Composable
private fun LegacyConversationRowsPreview() {
    Stage {
        LegacyConversationRow(
            context = androidx.compose.ui.platform.LocalContext.current,
            settings = fullRowSettings,
            convo = sampleConversation(1, "Priya Raman", "Are we still on for tomorrow?", unreadCount = 2),
            workProfile = false,
            showArchived = false,
            onClick = {}
        )
        LegacyConversationRow(
            context = androidx.compose.ui.platform.LocalContext.current,
            settings = fullRowSettings,
            convo = sampleConversation(2, "+15551239999", "Ok sounds good 👍", isMe = true),
            workProfile = false,
            showArchived = false,
            onClick = {}
        )
        LegacyConversationRow(
            context = androidx.compose.ui.platform.LocalContext.current,
            settings = minimalRowSettings,
            convo = sampleConversation(3, "Draft thread", "last message", draft = "typing a reply"),
            workProfile = false,
            showArchived = false,
            onClick = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Legacy action sheet and FAB", showBackground = true, widthDp = 380)
@Composable
private fun LegacySheetAndFabPreview() {
    Stage {
        LegacySheetActionRow(Icons.Rounded.PushPin, "Pin", MaterialTheme.colorScheme.onSurface, {})
        LegacySheetActionRow(Icons.Rounded.MarkChatRead, "Mark as read", MaterialTheme.colorScheme.onSurface, {})
        LegacySheetActionRow(Icons.Rounded.Drafts, "Mark as unread", MaterialTheme.colorScheme.onSurface, {})
        LegacySheetActionRow(Icons.Rounded.Delete, "Delete", MaterialTheme.colorScheme.error, {})
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LegacyStartChatFab(expanded = false) {}
            LegacyStartChatFab(expanded = true) {}
        }
    }
}

@PreviewLightDark
@Preview(name = "Legacy import radio group", showBackground = true, widthDp = 380)
@Composable
private fun LegacyImportRadioGroupPreview() {
    Stage {
        LegacyImportRadioGroup(
            options = listOf(
                "Keep a backup" to "Save messages to a local file",
                "Restore from backup" to "Read messages back from a file"
            ),
            selectedIndex = 1,
            onSelect = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Scheduled message rows", showBackground = true, widthDp = 380)
@Composable
private fun ScheduledRowPreview() {
    Stage {
        ScheduledRow(
            address = "+15551230001",
            label = "Scheduled",
            body = "See you at 6",
            timestamp = SAMPLE_TS,
            onOpenChat = {},
            onReschedule = {},
            onCancel = {}
        )
        ScheduledRow(
            address = "Work group",
            label = "Scheduled",
            body = "Reminder: pay the bill",
            timestamp = SAMPLE_TS_OLD,
            onOpenChat = {},
            onReschedule = {},
            onCancel = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Schedule a message picker", showBackground = true, widthDp = 380, heightDp = 640)
@Composable
private fun ChatSchedulePickerPreview() {
    // The picker is a multi-step dialog; the date and time steps are the two
    // that render distinct content, so both are shown.
    Stage {
        ChatSchedulePicker(
            visible = true,
            step = "date",
            conversationId = 1L,
            address = "+15551230001",
            draft = "See you at 6",
            scheduledDateMillis = SAMPLE_TS,
            scheduledHour = 18,
            scheduledMinute = 30,
            onDateSelected = {},
            onTimeSelected = { _, _ -> },
            onSchedule = { _ -> },
            onDismiss = {}
        )
    }
}
