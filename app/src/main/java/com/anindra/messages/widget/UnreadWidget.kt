package com.anindra.messages.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.anindra.messages.MessagesApplication
import com.anindra.messages.R
import com.anindra.messages.ui.theme.WidgetColors
import com.anindra.messages.ui.theme.widgetColors

/**
 * Unread messages only, as bubbles, with a compose pill kept visible in every
 * state but the locked one.
 */
class UnreadWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MessagesApplication
        val colors = widgetColors(context, app.repository.settings)
        val snapshot = loadWidgetSnapshot(context)
        provideContent {
            GlanceTheme(colors.providers) {
                UnreadCard(
                    snapshot = snapshot,
                    colors = colors,
                    rowLimit = WidgetData.rowCount(
                        LocalSize.current.height.value.toInt(),
                        rowDp = 46,
                        max = WidgetData.MAX_UNREAD_ROWS
                    )
                )
            }
        }
    }
}

class UnreadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UnreadWidget()
}

@Composable
internal fun UnreadCard(snapshot: WidgetSnapshot, colors: WidgetColors, rowLimit: Int) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(vertical = 6.dp)
    ) {
        if (snapshot.locked) {
            WidgetLockedCard()
        } else {
            WidgetUnreadHeader(snapshot.unreadTotal)
            if (snapshot.unread.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxWidth().height(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = LocalContext.current.getString(R.string.widget_all_caught_up),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            } else {
                snapshot.unread.take(rowLimit).forEach { WidgetBubble(it, colors) }
            }
            WidgetNewMessagePill()
        }
    }
}

@Composable
internal fun WidgetUnreadHeader(total: Int) {
    Row(
        modifier = GlanceModifier.height(28.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_unread_label),
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
        val label = LocalContext.current
            .getString(R.string.widget_unread_content_description, total)
        Box(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.primary)
                .cornerRadius(10.dp)
                .padding(horizontal = 6.dp)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = total.toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun WidgetBubble(item: WidgetConversation, colors: WidgetColors) {
    Column(
        modifier = GlanceModifier.height(46.dp)
            .padding(horizontal = 12.dp)
            .clickable(
                actionStartActivity(WidgetIntents.conversation(LocalContext.current, item.address))
            )
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetAvatar(item, colors)
            Text(
                text = item.title,
                modifier = GlanceModifier.defaultWeight().padding(horizontal = 8.dp),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Text(
                text = item.time,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                maxLines = 1
            )
        }
        Box(
            modifier = GlanceModifier.fillMaxWidth()
                .height(26.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(14.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = item.preview,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun WidgetNewMessagePill() {
    Box(
        modifier = GlanceModifier.fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 12.dp)
            .background(GlanceTheme.colors.primary)
            .cornerRadius(14.dp)
            .clickable(actionStartActivity(WidgetIntents.newMessage(LocalContext.current))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_new_message),
            style = TextStyle(
                color = GlanceTheme.colors.onPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}
