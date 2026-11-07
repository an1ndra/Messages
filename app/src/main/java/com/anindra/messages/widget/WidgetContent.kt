package com.anindra.messages.widget

import android.content.Context
import android.graphics.Bitmap
import com.anindra.messages.MessagesApplication
import com.anindra.messages.R
import com.anindra.messages.data.Conversation
import com.anindra.messages.sms.ConversationAvatar
import com.anindra.messages.ui.BidiText
import com.anindra.messages.ui.ContactDetails
import com.anindra.messages.ui.ConversationPreview
import com.anindra.messages.ui.formatListTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** One row of a widget: everything the Glance composition draws, already decided. */
internal data class WidgetConversation(
    val id: Long,
    val address: String,
    val title: String,
    val preview: String,
    val time: String,
    val unreadCount: Int,
    val avatarKey: String,
    val avatar: Bitmap?
)

/** What both widgets render from. [locked] carries no conversation data at all. */
internal data class WidgetSnapshot(
    val locked: Boolean,
    val list: List<WidgetConversation>,
    val unread: List<WidgetConversation>,
    val unreadTotal: Int
) {
    companion object {
        val Locked = WidgetSnapshot(true, emptyList(), emptyList(), 0)
    }
}

/**
 * Reads the data a widget shows. Nothing is queried while the app is locked:
 * the widget is on the launcher, outside the app's own gate, so it must not
 * hold names or message text even in memory for that case.
 */
internal suspend fun loadWidgetSnapshot(context: Context): WidgetSnapshot =
    withContext(Dispatchers.IO) {
        val app = context.applicationContext as MessagesApplication
        val repository = app.repository
        val settings = repository.settings
        if (WidgetData.isLocked(settings.appLockEnabled, settings.privacyModeEnabled)) {
            return@withContext WidgetSnapshot.Locked
        }
        val inbox = WidgetData.inbox(
            repository.conversations().first(),
            settings.unreadAtTopEnabled
        )
        val now = System.currentTimeMillis()
        fun row(convo: Conversation) =
            convo.toWidgetConversation(context, now, settings.hideLinks)
        WidgetSnapshot(
            locked = false,
            list = WidgetData.listRows(inbox, WidgetData.MAX_LIST_ROWS).map(::row),
            unread = WidgetData.unreadRows(inbox, WidgetData.MAX_UNREAD_ROWS).map(::row),
            unreadTotal = WidgetData.unreadTotal(inbox)
        )
    }

private fun Conversation.toWidgetConversation(
    context: Context,
    now: Long,
    hideLinks: Boolean
): WidgetConversation {
    val key = WidgetData.avatarKey(groupTitle, address)
    return WidgetConversation(
        id = id,
        address = address,
        title = ContactDetails.listLabel(groupTitle, name, address, BidiText.ltr(display)),
        preview = ConversationPreview.text(
            conversation = this,
            hideLinks = hideLinks,
            scheduled = null,
            draftPrefix = context.getString(R.string.chat_draft_prefix),
            scheduledPrefix = context.getString(R.string.chat_scheduled_prefix),
            yourPrefix = context.getString(R.string.convo_your_prefix)
        ),
        time = formatListTime(timestamp, now, context),
        unreadCount = unreadCount,
        avatarKey = key,
        // The same picture the inbox and the notification draw: the contact's
        // photo when there is one, else the silhouette on its palette colour.
        avatar = ConversationAvatar.forAddress(context, key).bitmap
    )
}
