package com.anindra.messages.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class WidgetKind { LIST, UNREAD }

/** The widgets the launcher actually holds, so a refresh costs nothing when none are placed. */
internal fun installedKinds(listPlaced: Boolean, unreadPlaced: Boolean): Set<WidgetKind> = buildSet {
    if (listPlaced) add(WidgetKind.LIST)
    if (unreadPlaced) add(WidgetKind.UNREAD)
}

/**
 * Pushes new data to the placed widgets. Debounced like the repository's own
 * listeners: a bulk import or a settings sweep notifies many times in a row and
 * only the last state matters.
 */
object WidgetRefresh {

    private const val DEBOUNCE_MS = 400L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var pending: Job? = null

    fun request(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            pending?.cancel()
            pending = scope.launch {
                delay(DEBOUNCE_MS)
                val manager = GlanceAppWidgetManager(app)
                val kinds = installedKinds(
                    manager.getGlanceIds(MessagesListWidget::class.java).isNotEmpty(),
                    manager.getGlanceIds(UnreadWidget::class.java).isNotEmpty()
                )
                for (kind in kinds) {
                    when (kind) {
                        WidgetKind.LIST -> MessagesListWidget().updateAll(app)
                        WidgetKind.UNREAD -> UnreadWidget().updateAll(app)
                    }
                }
            }
        }
    }
}
