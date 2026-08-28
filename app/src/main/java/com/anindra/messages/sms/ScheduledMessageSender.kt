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
        val wakeLock = ReceiverWakeLock.acquire(context, "scheduled-send")
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
                        repo.markMessageStatusSuspend(stored.id, "failed")
                    }
                }
                repo.deleteScheduledMessage(id)
            } catch (_: Exception) {
            } finally {
                wakeLock.safeRelease()
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
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val pendingIntent = buildPendingIntent(context, id, address, body, subId)
            // Prefer setAlarmClock — it does NOT require SCHEDULE_EXACT_ALARM,
            // and Android will not silently downgrade it like it does
            // setExactAndAllowWhileIdle on API 31+ without that permission.
            // The user-visible alarm clock affordance is acceptable for a
            // scheduled message use case.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val showIntent = android.app.PendingIntent.getActivity(
                    context, id.toInt(),
                    android.content.Intent(context, com.anindra.messages.MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
                alarmManager.setAlarmClock(info, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }

        fun cancel(context: Context, id: Long) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val pendingIntent = buildPendingIntent(context, id, "", "", -1)
            alarmManager.cancel(pendingIntent)
        }

        /** PendingIntent equality is based on the Intent's component + action
         *  + data + type (not extras), so [cancel] can pass placeholder extras
         *  and still match the same alarm that [schedule] registered. */
        private fun buildPendingIntent(
            context: Context, id: Long, address: String, body: String, subId: Int
        ): PendingIntent {
            val intent = Intent(context, ScheduledMessageSender::class.java).apply {
                action = ACTION_SEND
                if (address.isNotEmpty()) putExtra(EXTRA_ADDRESS, address)
                if (body.isNotEmpty()) putExtra(EXTRA_BODY, body)
                if (subId != -1) putExtra(EXTRA_SUB_ID, subId)
                putExtra(EXTRA_ID, id)
            }
            return PendingIntent.getBroadcast(
                context, id.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
