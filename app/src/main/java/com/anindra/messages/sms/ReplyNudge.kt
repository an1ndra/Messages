package com.anindra.messages.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.anindra.messages.MainActivity
import com.anindra.messages.MessagesApplication
import com.anindra.messages.R
import com.anindra.messages.data.ReplyNudgePolicy
import com.anindra.messages.data.ReplyNudgeRow

/**
 * The reminder for a message that was read and then forgotten (#484).
 *
 * An unread thread already has its own notification; this is for the thread the
 * user opened, meant to answer, and left. One card per conversation, tagged
 * `nudge`, on its own quiet channel — it is a reminder, so it never rings,
 * buzzes, badges, or wakes the screen. Tapping it opens the chat, and the card
 * is dropped the moment a message of ours lands in that thread.
 */
object ReplyNudge {

    /** Tag namespace, so a conversation id can never collide with a message id. */
    const val TAG = "nudge"

    const val CHANNEL_ID = "messages_nudge"

    fun idOf(conversationId: Long): Int = (conversationId and 0x7FFFFFFFL).toInt()

    fun cancel(context: Context, conversationId: Long) {
        try {
            androidx.core.app.NotificationManagerCompat.from(context)
                .cancel(TAG, idOf(conversationId))
        } catch (_: SecurityException) {
        }
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.activeNotifications.filter { it.tag == TAG }
            .forEach { nm.cancel(it.tag, it.id) }
    }

    /**
     * Posts what is due and drops what no longer is. One pass is the whole
     * state: a thread that got its reply, went unread, was archived, blocked,
     * muted or trashed simply stops qualifying, so the card goes with it.
     */
    fun reconcile(context: Context) {
        val app = context.applicationContext as MessagesApplication
        val settings = app.repository.settings
        if (!settings.replyNudgeEnabled || !NotificationHelper.canPost(context)) {
            cancelAll(context)
            return
        }
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val interval = ReplyNudgePolicy.intervalMillis(settings.replyNudgeHours)
        val due = mutableListOf<ReplyNudgeRow>()
        for (row in app.repository.replyNudgeRowsBlocking()) {
            val muted = NotificationHelper.channelImportanceFor(
                context, NotificationHelper.messageChannelFor(app, context, row.conversationId, row.address)
            ) == NotificationManager.IMPORTANCE_NONE
            if (ForegroundTracker.isConversationOpen(row.address)) continue
            if (!ReplyNudgePolicy.shouldNudge(
                    row, muted, settings.replyNudgedAt(row.conversationId), now, interval
                )
            ) continue
            due.add(row)
        }
        val live = due.map { idOf(it.conversationId) }.toHashSet()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.activeNotifications?.filter { it.tag == TAG && it.id !in live }
            ?.forEach { nm.cancel(it.tag, it.id) }
        due.forEach { post(context, it, now) }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_nudge_channel_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_nudge_channel_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun post(context: Context, row: ReplyNudgeRow, now: Long) {
        val app = context.applicationContext as MessagesApplication
        val label = app.repository.contactNameFor(row.address) ?: row.address
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra("open_conversation_address", row.address)
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val tap = PendingIntent.getActivity(
            context, idOf(row.conversationId), open, PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(
                if (app.repository.settings.privacyModeEnabled) {
                    context.getString(R.string.notif_nudge_title)
                } else {
                    String.format(context.getString(R.string.notif_nudge_title_to), label)
                }
            )
            .setContentText(
                if (app.repository.settings.privacyModeEnabled) {
                    context.getString(R.string.notif_nudge_private)
                } else {
                    row.snippet
                }
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(tap)
        try {
            androidx.core.app.NotificationManagerCompat.from(context)
                .notify(TAG, idOf(row.conversationId), builder.build())
            app.repository.settings.markReplyNudged(row.conversationId, now)
        } catch (_: SecurityException) {
        }
    }
}
