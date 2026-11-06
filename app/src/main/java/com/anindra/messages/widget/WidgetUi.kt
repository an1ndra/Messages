package com.anindra.messages.widget

import androidx.compose.runtime.Composable
import androidx.glance.text.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.anindra.messages.R
import com.anindra.messages.ui.theme.WidgetColors

@Composable
internal fun MessagesListCard(
    snapshot: WidgetSnapshot,
    colors: WidgetColors,
    rowLimit: Int
) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(vertical = WidgetData.VERTICAL_PADDING_DP.dp)
    ) {
        when {
            snapshot.locked -> WidgetLockedCard()
            snapshot.list.isEmpty() -> {
                WidgetListHeader()
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = LocalContext.current.getString(R.string.widget_no_conversations),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 13.sp
                        ),
                        maxLines = 1
                    )
                }
            }
            else -> {
                WidgetListHeader()
                snapshot.list.take(rowLimit).forEach { WidgetRow(it, colors) }
            }
        }
    }
}

@Composable
internal fun WidgetLockedCard() {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .clickable(actionStartActivity(WidgetIntents.inbox(LocalContext.current))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_lock),
            contentDescription = null,
            modifier = GlanceModifier.size(26.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
        )
        Text(
            text = LocalContext.current.getString(R.string.app_name),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
        Text(
            text = LocalContext.current.getString(R.string.widget_locked_body),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            maxLines = 1
        )
    }
}

@Composable
internal fun WidgetListHeader() {
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .height(WidgetData.HEADER_HEIGHT_DP.dp)
            .padding(horizontal = WidgetData.HORIZONTAL_PADDING_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier.defaultWeight()
                .fillMaxHeight()
                .clickable(actionStartActivity(WidgetIntents.inbox(LocalContext.current))),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = LocalContext.current.getString(R.string.app_name),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
        WidgetComposeButton()
    }
}

@Composable
internal fun WidgetComposeButton() {
    Box(
        modifier = GlanceModifier.size(WidgetData.COMPOSE_BUTTON_DP.dp)
            .background(GlanceTheme.colors.primary)
            .cornerRadius((WidgetData.COMPOSE_BUTTON_DP / 2).dp)
            .clickable(actionStartActivity(WidgetIntents.newMessage(LocalContext.current))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            style = TextStyle(
                color = GlanceTheme.colors.onPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
internal fun WidgetRow(item: WidgetConversation, colors: WidgetColors) {
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .height(WidgetData.ROW_HEIGHT_DP.dp)
            .padding(horizontal = WidgetData.HORIZONTAL_PADDING_DP.dp)
            .clickable(
                actionStartActivity(WidgetIntents.conversation(LocalContext.current, item.id))
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetAvatar(item, colors)
        Column(modifier = GlanceModifier.defaultWeight().padding(horizontal = 10.dp)) {
            Text(
                text = item.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Text(
                text = item.preview,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = item.time,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1
            )
            if (item.unreadCount > 0) {
                Box(
                    modifier = GlanceModifier.size(8.dp)
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(4.dp)
                ) {}
            }
        }
    }
}

@Composable
internal fun WidgetAvatar(item: WidgetConversation, colors: WidgetColors) {
    Box(
        modifier = GlanceModifier.size(WidgetData.AVATAR_DP.dp)
            .background(colors.avatarFill(item.avatarKey))
            .cornerRadius((WidgetData.AVATAR_DP / 2).dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.initials,
            style = TextStyle(
                color = colors.avatarInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}
