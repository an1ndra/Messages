package com.anindra.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anindra.messages.MessagesApplication

/**
 * Runs when the user swipes a conversation's notification away.
 *
 * The boot and app-update re-post exists so the launcher badge survives a
 * reboot, but it used to resurrect notifications that had been deliberately
 * dismissed — reported after installing a build (#345 follow-up). Recording
 * "dealt with up to now" here is what lets the re-post skip them while still
 * showing genuinely new messages.
 */
class NotificationDismissReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? MessagesApplication ?: return
        val conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, -1L)
        if (conversationId <= 0L) return
        app.repository.settings.markNotifCleared(conversationId, System.currentTimeMillis())
    }
}
