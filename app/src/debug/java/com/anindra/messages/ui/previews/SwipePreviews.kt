package com.anindra.messages.ui.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.anindra.messages.ui.SwipeConversationItem
import com.anindra.messages.ui.SwipeableConversationItem
import com.anindra.messages.ui.legacy.SwipeableConversationItem as LegacySwipeableConversationItem

/**
 * The swipe wrappers, previewed in their resting state.
 *
 * A `SwipeToDismissBox` starts settled, so a preview shows the row and its
 * background affordance at rest rather than mid-gesture. The two configured
 * actions are shown side by side because the reported bug on this wrapper was
 * left and right resolving to the same action, which is invisible at rest and
 * obvious the moment the two are next to each other.
 */
@PreviewLightDark
@Preview(name = "Swipeable row (new UI)", showBackground = true, widthDp = 380)
@Composable
private fun SwipeableRowPreview() {
    Stage {
        SwipeableConversationItem(
            context = LocalContext.current,
            settings = fullRowSettings.copy(
                swipeLeftAction = com.anindra.messages.data.SwipeAction.MARK_READ_UNREAD,
                swipeRightAction = com.anindra.messages.data.SwipeAction.DELETE
            ),
            swipeEnabled = true,
            convo = sampleConversation(1, "Priya Raman", "Swipe me either way", unreadCount = 1),
            workProfile = false,
            showArchived = false,
            onClick = {},
            onDelete = {}
        )
        SwipeableConversationItem(
            context = LocalContext.current,
            settings = fullRowSettings,
            swipeEnabled = false,
            convo = sampleConversation(2, "Swipe disabled", "Gestures are off"),
            workProfile = false,
            showArchived = false,
            onClick = {},
            onDelete = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Swipeable row (legacy UI)", showBackground = true, widthDp = 380)
@Composable
private fun LegacySwipeableRowPreview() {
    Stage {
        LegacySwipeableConversationItem(
            context = LocalContext.current,
            settings = fullRowSettings,
            swipeEnabled = true,
            convo = sampleConversation(1, "Priya Raman", "Swipe me either way", unreadCount = 1),
            workProfile = false,
            showArchived = false,
            onClick = {},
            onDelete = {}
        )
    }
}

@PreviewLightDark
@Preview(name = "Swipe row (bare wrapper)", showBackground = true, widthDp = 380)
@Composable
private fun SwipeRowPreview() {
    Stage {
        SwipeConversationItem(
            context = LocalContext.current,
            settings = fullRowSettings,
            convo = sampleConversation(1, "Priya Raman", "Wrapped without the extra layer"),
            workProfile = false,
            onClick = {},
            onDelete = {},
            onArchive = {},
            onToggleRead = {},
            onTogglePin = {},
            onBlock = {}
        )
    }
}
