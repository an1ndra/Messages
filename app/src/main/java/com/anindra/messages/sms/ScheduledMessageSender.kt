package com.anindra.messages.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anindra.messages.MessagesApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sends a queued message when its alarm fires, and answers the two actions the
 * schedule's ongoing card offers (#475): send it now, or drop it.
 */
class ScheduledMessageSender : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val pendingResult = goAsync()
        val wakeLock = ReceiverWakeLock.acquire(context, "scheduled-send")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MessagesApplication
                val repo = app.repository
                when (intent.action) {
                    ACTION_CANCEL -> {
                        cancel(context, id)
                        repo.deleteScheduledMessage(id)
                        ScheduledNotifications.cancel(context, id)
                    }
                    ACTION_SEND_NOW -> {
                        // Read the row back: the card can outlive a reschedule,
                        // and the alarm's own extras would then be stale.
                        val row = repo.scheduledMessageById(id)
                        if (row != null) {
                            send(context, row.id, row.address, row.body, row.subId)
                            repo.deleteScheduledMessage(row.id)
                        } else {
                            ScheduledNotifications.cancel(context, id)
                        }
                    }
                    else -> {
                        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return@launch
                        val body = intent.getStringExtra(EXTRA_BODY) ?: return@launch
                        val subId = intent.getIntExtra(EXTRA_SUB_ID, -1)
                        send(context, id, address, body, subId)
                        repo.deleteScheduledMessage(id)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "scheduled send failed", e)
            } finally {
                wakeLock.safeRelease()
                pendingResult.finish()
            }
        }
    }

    private fun send(context: Context, id: Long, address: String, body: String, subId: Int) {
        try {
            val repo = (context.applicationContext as MessagesApplication).repository
            val stored = repo.sendText(repo.getOrCreateConversationBlocking(address), body)
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
        } finally {
            ScheduledNotifications.cancel(context, id)
        }
    }

    companion object {
        private const val TAG = "ScheduledSend"
        const val EXTRA_ID = "scheduled_id"
        const val EXTRA_ADDRESS = "scheduled_address"
        const val EXTRA_BODY = "scheduled_body"
        const val EXTRA_SUB_ID = "scheduled_sub_id"
        private const val ACTION_SEND = "com.anindra.messages.SCHEDULED_SEND"

        /** The card's two actions. Distinct actions keep their PendingIntents
         *  distinct from the alarm's, which shares the request code. */
        const val ACTION_SEND_NOW = "com.anindra.messages.SCHEDULED_SEND_NOW"
        const val ACTION_CANCEL = "com.anindra.messages.SCHEDULED_CANCEL"

        fun schedule(context: Context, id: Long, address: String, body: String, subId: Int, triggerAtMillis: Long) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            // The wrapped Intent is explicit (component + package) and built with
            // plain statements in the SAME method as the AlarmManager sink, so
            // CodeQL's intra-procedural explicit-intent sanitizer applies.
            val intent = Intent(context, ScheduledMessageSender::class.java)
            intent.setPackage(context.packageName)
            intent.action = ACTION_SEND
            if (address.isNotEmpty()) intent.putExtra(EXTRA_ADDRESS, address)
            if (body.isNotEmpty()) intent.putExtra(EXTRA_BODY, body)
            if (subId != -1) intent.putExtra(EXTRA_SUB_ID, subId)
            intent.putExtra(EXTRA_ID, id)
            val pendingIntent = PendingIntent.getBroadcast(
                context, id.toInt(), intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            // Prefer setAlarmClock — it does NOT require SCHEDULE_EXACT_ALARM,
            // and Android will not silently downgrade it like it does
            // setExactAndAllowWhileIdle on API 31+ without that permission.
            // The user-visible alarm clock affordance is acceptable for a
            // scheduled message use case.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val showIntent = Intent(context, com.anindra.messages.MainActivity::class.java)
                showIntent.setPackage(context.packageName)
                val showPi = PendingIntent.getActivity(
                    context, id.toInt(), showIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
                val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPi)
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
                PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
