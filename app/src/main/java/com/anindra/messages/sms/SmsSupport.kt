package com.anindra.messages.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.anindra.messages.MainActivity
import com.anindra.messages.R

object NotificationHelper {
    private const val CHANNEL_ID = "messages"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "New message notifications" }
        nm.createNotificationChannel(channel)
    }

    private fun canPost(context: Context): Boolean {
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        if (!app.repository.settings.notificationsEnabled) return false
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return true
    }

    fun show(context: Context, from: String, body: String) {
        // create the channel before any early-return: notify() with an unknown
        // channel id is a silent no-op, so the first-ever post must have it ready
        ensureChannel(context)
        if (!canPost(context)) return

        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val convoId = app.repository.conversationIdForAddress(from)
        if (convoId != null && !app.repository.getConversationNotificationsEnabled(convoId)) return

        // stable per-conversation id: hashCode collisions between different
        // senders would otherwise overwrite each other's PendingIntents and
        // dismiss the wrong notification on quick-reply
        val notifId = (convoId ?: from.hashCode().toLong()).toInt()
        val reqCode = notifId and 0x7FFFFFFF

        playReceiveSound(context)

        val privacyMode = app.repository.settings.privacyModeEnabled

        val tap = PendingIntent.getActivity(
            context, reqCode,
            Intent(context, MainActivity::class.java).apply {
                putExtra("open_conversation_address", from)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replyIntent = PendingIntent.getBroadcast(
            context, reqCode,
            QuickReplyReceiver.createReplyIntent(context, from, from)
                .putExtra(QuickReplyReceiver.EXTRA_NOTIF_ID, notifId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder("quick_reply").setLabel("Reply").build()

        val replyAction = NotificationCompat.Action.Builder(
            0, "Reply", replyIntent
        ).addRemoteInput(remoteInput).build()

        val title = if (privacyMode) "New message" else from
        val text = if (privacyMode) "You have a new message" else body

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .addAction(replyAction)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notif)
        } catch (_: SecurityException) {
        }
    }

    /** Posted when an outgoing SMS/MMS fails to hand off to the radio. */
    fun showSendFailed(context: Context, to: String) {
        ensureChannel(context)
        if (!canPost(context)) return

        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val failId = (app.repository.conversationIdForAddress(to) ?: to.hashCode().toLong()).toInt()

        // "failed" tag decouples failure notifications from incoming-message ids
        val tap = PendingIntent.getActivity(
            context, failId and 0x7FFFFFFF,
            Intent(context, MainActivity::class.java).apply {
                putExtra("open_conversation_address", to)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val privacyMode = app.repository.settings.privacyModeEnabled
        val failText = if (privacyMode) "Couldn't send message. Tap to retry."
            else "Couldn't send message to $to. Tap to retry."

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Message not delivered")
            .setContentText(failText)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()

        try {
            NotificationManagerCompat.from(context).notify("failed", failId, notif)
        } catch (_: SecurityException) {
        }
    }

    fun playSentSound(context: Context) = playSound(context, incoming = false)

    private fun playReceiveSound(context: Context) =
        playSound(context, incoming = true)

    private fun playSound(context: Context, incoming: Boolean) {
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        if (!app.repository.settings.soundsEnabled) return
        val key = if (incoming) app.repository.settings.incomingSound
        else app.repository.settings.outgoingSound
        when (key) {
            com.anindra.messages.data.SettingsStore.SOUND_SILENT -> return
            com.anindra.messages.data.SettingsStore.SOUND_DEFAULT ->
                playTone(context, if (incoming) android.media.ToneGenerator.TONE_PROP_BEEP2 else android.media.ToneGenerator.TONE_PROP_ACK)
            else -> com.anindra.messages.data.SoundStore.play(context, key)
        }
    }

    private fun playTone(context: Context, tone: Int) {
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        if (!app.repository.settings.soundsEnabled) return
        try {
            val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(tone, 150)
            android.os.Handler(context.mainLooper).postDelayed({ tg.release() }, 400)
        } catch (_: Exception) {
        }
    }
}

object SmsSender {

    private fun manager(subscriptionId: Int): android.telephony.SmsManager =
        if (subscriptionId != -1)
            android.telephony.SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        else
            android.telephony.SmsManager.getDefault()

    /**
     * Sends via the framework with sent/delivery callbacks; SmsStatusReceiver
     * receives them and flips the stored row to sent/failed/delivered.
     */
    fun send(
        context: Context,
        messageId: Long,
        address: String,
        body: String,
        subscriptionId: Int = -1,
        wantDeliveryReport: Boolean = false
    ): Boolean = try {
        val sm = manager(subscriptionId)
        val sent = PendingIntent.getBroadcast(
            context, (messageId % Int.MAX_VALUE).toInt(),
            Intent(SmsStatusReceiver.ACTION_SMS_SENT)
                .setPackage(context.packageName)
                .putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val delivered = if (wantDeliveryReport) PendingIntent.getBroadcast(
            context, (messageId % Int.MAX_VALUE).toInt(),
            Intent(SmsStatusReceiver.ACTION_SMS_DELIVERED)
                .setPackage(context.packageName)
                .putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) else null

        val parts = sm.divideMessage(body)
        if (parts.size <= 1) {
            sm.sendTextMessage(address, null, body, sent, delivered)
        } else {
            sm.sendMultipartTextMessage(
                address, null, parts,
                ArrayList(listOf(sent)),
                delivered?.let { ArrayList(listOf(it)) }
            )
        }
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Best-effort MMS hand-off. On an emulator without an MMSC this typically
     * throws or reports failure immediately; callers mark the row as failed.
     */
    fun sendMms(
        context: Context,
        messageId: Long,
        address: String,
        media: Uri,
        subscriptionId: Int = -1
    ): Boolean = try {
        val sent = PendingIntent.getBroadcast(
            context, (messageId % Int.MAX_VALUE).toInt(),
            Intent(SmsStatusReceiver.ACTION_MMS_SENT)
                .setPackage(context.packageName)
                .putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager(subscriptionId).sendMultimediaMessage(context, media, null, null, sent)
        true
    } catch (_: Exception) {
        false
    }
}
