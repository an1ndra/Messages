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
import com.anindra.messages.hideUrls

object NotificationHelper {
    private const val CHANNEL_ID = "messages"

    /**
     * (Re)creates the notification channel using the system default notification
     * sound — never a custom tone. When the "Receive sound" setting is off the
     * channel is silent. createNotificationChannel() upserts in place, so this is
     * safe to call on every post and reflects live setting changes (the default
     * sound must be set EXPLICITLY, or an update leaves a legacy custom tone in place).
     */
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val soundOn = app.repository.settings.receiveSoundEnabled
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New message notifications"
                if (soundOn) {
                    setSound(
                        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                        android.app.Notification.AUDIO_ATTRIBUTES_DEFAULT
                    )
                } else {
                    setSound(null, null)
                }
            }
        )
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
        if (convoId != null && !app.repository.getConversationNotificationsEnabledBlocking(convoId)) return

        // stable per-conversation id: hashCode collisions between different
        // senders would otherwise overwrite each other's PendingIntents and
        // dismiss the wrong notification on quick-reply
        val notifId = (convoId ?: from.hashCode().toLong()).toInt()
        val reqCode = notifId and 0x7FFFFFFF

        val privacyMode = app.repository.settings.privacyModeEnabled

        val tapIntent = Intent(context, MainActivity::class.java)
        tapIntent.putExtra("open_conversation_address", from)
        tapIntent.setPackage(context.packageName)
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(
            context, reqCode,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val replyIntent = PendingIntent.getBroadcast(
            context, reqCode,
            QuickReplyReceiver.createReplyIntent(context, from, from)
                .putExtra(QuickReplyReceiver.EXTRA_NOTIF_ID, notifId),
            PendingIntent.FLAG_MUTABLE
        )

        val remoteInput = RemoteInput.Builder("quick_reply").setLabel("Reply").build()

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_reply, "Reply", replyIntent
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .addRemoteInput(remoteInput).build()

        // Resolve the stored contact name from the address book so notifications
        // show the person, not the raw number (issue #184).
        val senderName = app.repository.contactNameFor(from) ?: from
        val title = if (privacyMode) "New message" else senderName
        val text = when {
            privacyMode -> "You have a new message"
            app.repository.settings.hideLinks -> hideUrls(body)
            else -> body
        }

        // Use the system default notification sound. Notification channels are
        // immutable after creation (a legacy install may still carry the old
        // custom tone), so the sound is applied HERe on each notification —
        // builder-level sound always wins over the channel unless the user has
        // locked the channel's sound.
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .addAction(replyAction)
        if (app.repository.settings.receiveSoundEnabled) {
            val defSound =
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            // NotificationCompat.Builder.setSound() does not stick on the emitted
            // notification (dumpsys comes back sound=null), so set the field
            // directly on the platform Notification — it drives actual playback.
            val n = builder.build()
            n.sound = defSound
            notify(context, notifId, n)
        } else {
            notify(context, notifId, builder.setSilent(true).build())
        }
    }

    private fun notify(context: Context, notifId: Int, notif: android.app.Notification) {
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
        val tapIntent = Intent(context, MainActivity::class.java)
        tapIntent.putExtra("open_conversation_address", to)
        tapIntent.setPackage(context.packageName)
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(
            context, failId and 0x7FFFFFFF,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE
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

    fun playSentSound(context: Context) = playSound(context)

    private fun playSound(context: Context) {
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        if (!app.repository.settings.sendSoundEnabled) return
        try {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.notification_sound}")
            val mp = android.media.MediaPlayer()
            mp.setDataSource(context, uri)
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setOnCompletionListener { it.release() }
            mp.prepareAsync()
            mp.setOnPreparedListener { it.start() }
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
                .setComponent(android.content.ComponentName(context, SmsStatusReceiver::class.java))
                .putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_IMMUTABLE
        )
        val delivered = if (wantDeliveryReport) PendingIntent.getBroadcast(
            context, (messageId % Int.MAX_VALUE).toInt(),
            Intent(SmsStatusReceiver.ACTION_SMS_DELIVERED)
                .setPackage(context.packageName)
                .setComponent(android.content.ComponentName(context, SmsStatusReceiver::class.java))
                .putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_IMMUTABLE
        ) else null

        val parts = sm.divideMessage(body)
        val dest = normalizeAddress(address)
        if (parts.size <= 1) {
            sm.sendTextMessage(dest, null, body, sent, delivered)
        } else {
            sm.sendMultipartTextMessage(
                dest, null, parts,
                ArrayList(listOf(sent)),
                delivered?.let { ArrayList(listOf(it)) }
            )
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Strips formatting from stored address; keeps digits and a leading '+'. */
    private fun normalizeAddress(address: String): String {
        val digits = address.filter { it.isDigit() }
        if (digits.isEmpty()) return address
        return if (address.trimStart().startsWith("+")) "+$digits" else digits
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
                .setComponent(android.content.ComponentName(context, SmsStatusReceiver::class.java))
                .putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_IMMUTABLE
        )
        manager(subscriptionId).sendMultimediaMessage(context, media, null, null, sent)
        true
    } catch (_: Exception) {
        false
    }
}
