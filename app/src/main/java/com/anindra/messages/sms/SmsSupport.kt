package com.anindra.messages.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.util.Log
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
        if (!canPost(context)) return

        playReceiveSound(context)

        ensureChannel(context)

        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                putExtra("open_conversation_address", from)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(from)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(from.hashCode(), notif)
        } catch (_: SecurityException) {
        }
    }

    /** Posted when an outgoing SMS/MMS fails to hand off to the radio. */
    fun showSendFailed(context: Context, to: String) {
        if (!canPost(context)) return
        ensureChannel(context)

        val tap = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).apply {
                putExtra("open_conversation_address", to)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Message not delivered")
            .setContentText("Couldn't send message to $to. Tap to retry.")
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()

        try {
            NotificationManagerCompat.from(context).notify("failed:$to".hashCode(), notif)
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
    } catch (e: Exception) {
        Log.e("SmsSend", "text handoff failed", e)
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
