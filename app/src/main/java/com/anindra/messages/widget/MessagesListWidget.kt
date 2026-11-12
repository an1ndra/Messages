package com.anindra.messages.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.anindra.messages.MessagesApplication
import com.anindra.messages.ui.theme.widgetColors

/**
 * The conversation list, sized to the cell the launcher gave it: [SizeMode.Exact]
 * re-runs the composition for each size the launcher reports, and
 * [WidgetData.rowCount] turns that height into a row count, so a taller widget
 * shows more chats instead of scaling the same four.
 */
class MessagesListWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MessagesApplication
        val colors = widgetColors(context, app.repository.settings)
        val snapshot = loadWidgetSnapshot(context)
        provideContent {
            GlanceTheme(colors.providers) {
                MessagesListCard(
                    snapshot = snapshot,
                    colors = colors,
                    rowLimit = WidgetData.rowCount(LocalSize.current.height.value.toInt())
                )
            }
        }
    }
}

class MessagesListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MessagesListWidget()
}
