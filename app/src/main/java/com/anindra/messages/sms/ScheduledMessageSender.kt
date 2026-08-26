package com.anindra.messages.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anindra.messages.MessagesApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScheduledMessageSender : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return
        val subId = intent.getIntExtra(EXTRA_SUB_ID, -1)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MessagesApplication
                val repo = app.repository
                val stored = repo.sendText(
                    repo.getOrCreateConversationBlocking(address),
                    body
                )
                if (stored != null) {
                    try {
                        SmsSender.send(
                            context, stored.id, address, body, subId,
                            repo.settings.deliveryReportsEnabled
                        )
                    } catch (_: Exception) {
                        // hand-off failed but the text is durably stored — surface it as
                        // a retriable failed message instead of leaving status "sending"
                        repo.markMessageStatusSuspend(stored.id, "failed")
                    }
                }
                // the schedule entry must always go away once the content lives on
                // as a normal message row; keeping it would leave a ghost that can
                // never fire again (the alarm is one-shot)
                repo.deleteScheduledMessage(id)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "scheduled_id"
        const val EXTRA_ADDRESS = "scheduled_address"
        const val EXTRA_BODY = "scheduled_body"
        const val EXTRA_SUB_ID = "scheduled_sub_id"
        private const val ACTION_SEND = "com.anindra.messages.SCHEDULED_SEND"

        fun schedule(context: Context, id: Long, address: String, body: String, subId: Int, triggerAtMillis: Long) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, ScheduledMessageSender::class.java).apply {
                action = ACTION_SEND
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_SUB_ID, subId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        fun cancel(context: Context, id: Long) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val intent = Intent(context, ScheduledMessageSender::class.java).apply {
                action = ACTION_SEND
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager?.cancel(pendingIntent)
        }
    }
}
