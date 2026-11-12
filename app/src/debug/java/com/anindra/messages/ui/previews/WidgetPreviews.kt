package com.anindra.messages.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import com.anindra.messages.widget.MessagesListCard
import com.anindra.messages.widget.UnreadCard
import com.anindra.messages.widget.WidgetAvatar
import com.anindra.messages.widget.WidgetBubble
import com.anindra.messages.widget.WidgetComposeButton
import com.anindra.messages.ui.theme.WidgetColors
import com.anindra.messages.widget.WidgetConversation
import com.anindra.messages.widget.WidgetListHeader
import com.anindra.messages.widget.WidgetLockedCard
import com.anindra.messages.widget.WidgetNewMessagePill
import com.anindra.messages.widget.WidgetRow
import com.anindra.messages.widget.WidgetSnapshot
import com.anindra.messages.widget.WidgetUnreadHeader
import com.anindra.messages.ui.theme.DarkColors
import com.anindra.messages.ui.theme.LightColors

private val widgetColors = WidgetColors(
    ColorProviders(LightColors, DarkColors),
    LightColors,
    DarkColors
)

private val sampleItems = listOf(
    WidgetConversation(
        id = 1L, address = "+15551230001", title = "Maya Chen", preview = "See you at 6",
        time = "18:04", unreadCount = 2, avatarKey = "+15551230001", avatar = null
    ),
    WidgetConversation(
        id = 2L, address = "+15551230002", title = "Dad", preview = "You: on my way",
        time = "17:20", unreadCount = 0, avatarKey = "+15551230002", avatar = null
    ),
    WidgetConversation(
        id = 3L, address = "+15551230003", title = "Weekend plans", preview = "Priya: Sunday works",
        time = "Wed", unreadCount = 1, avatarKey = "Weekend plans", avatar = null
    ),
    WidgetConversation(
        id = 4L, address = "+15551230004", title = "+1 555 123 0004", preview = "Draft: running late",
        time = "Tue", unreadCount = 0, avatarKey = "+15551230004", avatar = null
    )
)

private val sampleSnapshot = WidgetSnapshot(
    locked = false,
    list = sampleItems,
    unread = sampleItems.filter { it.unreadCount > 0 },
    unreadTotal = 3
)

private val lockedSnapshot = WidgetSnapshot.Locked

private val emptySnapshot = WidgetSnapshot(false, emptyList(), emptyList(), 0)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 320, heightDp = 130)
@Composable
private fun MessagesListCardPreview() {
    GlanceTheme(widgetColors.providers) {
        MessagesListCard(sampleSnapshot, widgetColors, rowLimit = 3)
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 320, heightDp = 130)
@Composable
private fun MessagesListCardLockedPreview() {
    GlanceTheme(widgetColors.providers) {
        MessagesListCard(lockedSnapshot, widgetColors, rowLimit = 3)
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 320, heightDp = 130)
@Composable
private fun MessagesListCardEmptyPreview() {
    GlanceTheme(widgetColors.providers) {
        MessagesListCard(emptySnapshot, widgetColors, rowLimit = 3)
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 320, heightDp = 130)
@Composable
private fun WidgetLockedCardPreview() {
    GlanceTheme(widgetColors.providers) { WidgetLockedCard() }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 320, heightDp = 32)
@Composable
private fun WidgetListHeaderPreview() {
    GlanceTheme(widgetColors.providers) {
        Box(GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp)) { WidgetListHeader() }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 40, heightDp = 40)
@Composable
private fun WidgetComposeButtonPreview() {
    GlanceTheme(widgetColors.providers) {
        Box(GlanceModifier.size(40.dp).padding(8.dp)) { WidgetComposeButton() }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 320, heightDp = 30)
@Composable
private fun WidgetRowPreview() {
    GlanceTheme(widgetColors.providers) { WidgetRow(sampleItems.first(), widgetColors) }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 40, heightDp = 40)
@Composable
private fun WidgetAvatarPreview() {
    GlanceTheme(widgetColors.providers) {
        Box(GlanceModifier.size(40.dp).padding(8.dp)) {
            WidgetAvatar(sampleItems.first(), widgetColors)
        }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 320, heightDp = 150)
@Composable
private fun UnreadCardPreview() {
    GlanceTheme(widgetColors.providers) {
        UnreadCard(
            WidgetSnapshot(false, sampleItems, sampleItems.filter { it.unreadCount > 0 }, 3),
            widgetColors,
            rowLimit = 2
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 320, heightDp = 150)
@Composable
private fun UnreadCardEmptyPreview() {
    GlanceTheme(widgetColors.providers) {
        UnreadCard(emptySnapshot, widgetColors, rowLimit = 2)
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 320, heightDp = 32)
@Composable
private fun WidgetUnreadHeaderPreview() {
    GlanceTheme(widgetColors.providers) {
        Box(GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            WidgetUnreadHeader(12)
        }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 320, heightDp = 46)
@Composable
private fun WidgetBubblePreview() {
    GlanceTheme(widgetColors.providers) {
        Box(GlanceModifier.fillMaxWidth()) { WidgetBubble(sampleItems.first(), widgetColors) }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 320, heightDp = 28)
@Composable
private fun WidgetNewMessagePillPreview() {
    GlanceTheme(widgetColors.providers) {
        Box(GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp)) { WidgetNewMessagePill() }
    }
}
