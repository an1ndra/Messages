package com.anindra.messages.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.anindra.messages.MainActivity
import com.anindra.messages.MessagesApplication
import com.anindra.messages.R
import com.anindra.messages.data.ScheduledMessage

/**
 * The shade's view of a queued message (#475).
 *
 * A schedule is invisible between the moment it is made and the moment it
 * fires: there is no countdown and no way to reach it except Settings. One
 * ongoing card per pending row fixes that — the system animates the countdown
 * itself (a chronometer, so no wakeups), and the two actions the shade can
 * offer are the two the row has: send it now, or drop it.
 *
 * The card is a state, not an event: silent channel, never a heads-up.
 */
object ScheduledNotifications {

    /** Tag namespace, so a row id can never collide with a message id. */
    const val TAG = "scheduled"

    /**
     * Its own channel: the countdown is not a message arrival and must not
     * buzz, ring or badge. Created on first post, then owned by the user.
     */
    const val CHANNEL_ID = "messages_scheduled"

    fun idOf(messageId: Long): Int = (messageId and 0x7FFFFFFFL).toInt()

    /** Minutes left, rounded up: the length of the promoted card's segment. */
    fun minutesLeft(now: Long, triggerAt: Long): Int =
        (((triggerAt - now).coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()

    fun post(context: Context, row: ScheduledMessage) {
        if (!NotificationHelper.canPost(context)) return
        ensureChannel(context)
        val app = context.applicationContext as MessagesApplication
        val privacy = app.repository.settings.privacyModeEnabled
        val label = app.repository.contactNameFor(row.address) ?: row.address
        val base = builder(context, row, label, privacy).build()
        try {
            NotificationManagerCompat.from(context).notify(TAG, idOf(row.id), promote(context, base, row))
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, messageId: Long) {
        try {
            NotificationManagerCompat.from(context).cancel(TAG, idOf(messageId))
        } catch (_: SecurityException) {
        }
    }

    /**
     * Drops cards whose rows are gone. Only the tag is touched: message
     * notifications share the shade and must survive.
     */
    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.activeNotifications.filter { it.tag == TAG }
            .forEach { nm.cancel(it.tag, it.id) }
    }

    /**
     * Re-posts every pending row and drops the stale cards. The shade is wiped
     * by a boot or an app update, and posting at schedule time can be refused
     * (notifications off, permission never asked) with the row still waiting —
     * so boot, update and app start all reconcile here instead of trusting
     * that the post at schedule time survived.
     */
    fun sync(context: Context) {
        val app = context.applicationContext as MessagesApplication
        val rows = app.repository.scheduledMessagesBlocking()
        if (!NotificationHelper.canPost(context)) {
            cancelAll(context)
            return
        }
        val live = rows.map { idOf(it.id) }.toHashSet()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.activeNotifications?.filter { it.tag == TAG && it.id !in live }
            ?.forEach { nm.cancel(it.tag, it.id) }
        rows.forEach { post(context, it) }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_scheduled_channel_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_scheduled_channel_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun builder(
        context: Context,
        row: ScheduledMessage,
        label: String,
        privacy: Boolean
    ): NotificationCompat.Builder {
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra("open_conversation_address", row.address)
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val tap = PendingIntent.getActivity(
            context, idOf(row.id), open, PendingIntent.FLAG_IMMUTABLE
        )
        // Same component and request code as the alarm; the action is what
        // keeps the three PendingIntents distinct, and the receiver routes on it.
        val sendIntent = Intent(context, ScheduledMessageSender::class.java).apply {
            action = ScheduledMessageSender.ACTION_SEND_NOW
            setPackage(context.packageName)
            putExtra(ScheduledMessageSender.EXTRA_ID, row.id)
        }
        val sendNow = PendingIntent.getBroadcast(
            context, idOf(row.id), sendIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = Intent(context, ScheduledMessageSender::class.java).apply {
            action = ScheduledMessageSender.ACTION_CANCEL
            setPackage(context.packageName)
            putExtra(ScheduledMessageSender.EXTRA_ID, row.id)
        }
        val cancel = PendingIntent.getBroadcast(
            context, idOf(row.id), cancelIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(context.getString(R.string.notif_scheduled_title))
            .setContentText(
                if (privacy) context.getString(R.string.notif_scheduled_private) else row.body
            )
            .setSubText(String.format(context.getString(R.string.notif_scheduled_to), label))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setWhen(row.timestamp)
            .setShowWhen(true)
            // The system draws the countdown and repaints it every second, so
            // the card stays live without a ticker of our own.
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(tap)
            .addAction(0, context.getString(R.string.notif_scheduled_send_now), sendNow)
            .addAction(0, context.getString(R.string.notif_scheduled_cancel), cancel)
    }

    /**
     * Android 16 promotes an ongoing card into a live surface, and a queued
     * send is the textbook case. Below 36 — or when the user has promoted
     * notifications switched off — the chronometer alone carries the countdown.
     */
    private fun promote(context: Context, base: Notification, row: ScheduledMessage): Notification {
        if (Build.VERSION.SDK_INT < 36) return base
        val nm = context.getSystemService(NotificationManager::class.java) ?: return base
        if (!nm.canPostPromotedNotifications()) return base
        val minutes = minutesLeft(System.currentTimeMillis(), row.timestamp)
        val promoted = Notification.Builder.recoverBuilder(context, base).apply {
            setShortCriticalText(
                String.format(context.getString(R.string.notif_scheduled_minutes), minutes)
            )
            setStyle(
                Notification.ProgressStyle()
                    // One static segment: the countdown itself is the
                    // chronometer, so a tracker crawling on a timer we do not
                    // run would be a lie.
                    .setStyledByProgress(false)
                    .setProgressSegments(
                        listOf(Notification.ProgressStyle.Segment(minutes.coerceAtLeast(1)))
                    )
            )
        }.build()
        promoted.flags = promoted.flags or Notification.FLAG_PROMOTED_ONGOING
        return promoted
    }
}
