package com.anindra.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.anindra.messages.MessagesApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Trashes the newest incoming message in a conversation from its notification
 * (#285). Deleting means moving to Trash, so it stays recoverable there.
 *
 * When that was the last live message the conversation has nothing left to
 * show, so it goes the same way (#341). Leaving it behind is what put an empty
 * row with no content in the inbox, because the empty-chat prune otherwise only
 * runs when a chat is exited — and a notification delete never opens one.
 */
class DeleteMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, address.hashCode())

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MessagesApplication
                val repo = app.repository
                val convoId = repo.conversationIdForAddress(address)
                val messageId = convoId?.let { repo.latestReceivedMessageIdSuspend(it) }
                if (messageId != null) {
                    repo.deleteMessageSuspend(messageId)
                    // Skip the chat the user is looking at: deleting a row under
                    // the open screen would pull the conversation out from under
                    // it, and the exit path prunes it anyway.
                    if (convoId != null && convoId != repo.viewedConversationId) {
                        repo.trashConversationIfEmptySuspend(convoId)
                    }
                    try {
                        NotificationManagerCompat.from(context).cancel(notifId)
                    } catch (_: SecurityException) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DELETE = "com.anindra.messages.NOTIFICATION_DELETE"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
