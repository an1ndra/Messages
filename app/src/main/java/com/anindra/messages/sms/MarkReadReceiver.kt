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

class MarkReadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, address.hashCode())

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MessagesApplication
                val repo = app.repository
                val convoId = repo.conversationIdForAddress(address)
                convoId?.let { repo.markReadSuspend(it) }
                NotificationManagerCompat.from(context).cancel(notifId)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val ACTION_MARK_READ = "com.anindra.messages.MARK_READ"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
