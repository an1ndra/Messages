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
import com.anindra.messages.data.SettingsStore
import com.anindra.messages.hideUrls

object NotificationHelper {
    // NotificationChannel sound is immutable after creation, so each distinct
    // sound (and the silent case) gets its own channel id. Changing the setting
    // creates a fresh channel with the right tone; the stale variants are deleted.
    private const val CHANNEL_PREFIX = "messages"
    private val CHANNEL_VARIANTS = listOf(
        "${CHANNEL_PREFIX}_default",
        "${CHANNEL_PREFIX}_app",
        "${CHANNEL_PREFIX}_dragon",
        "${CHANNEL_PREFIX}_uf09",
        "${CHANNEL_PREFIX}_uf062",
        "${CHANNEL_PREFIX}_silent",
        CHANNEL_PREFIX,
    )

    private var previewPlayer: android.media.MediaPlayer? = null

    private fun channelId(context: Context): String {
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val s = app.repository.settings
        if (!s.receiveSoundEnabled) return "${CHANNEL_PREFIX}_silent"
        return when (s.notificationSound) {
            SettingsStore.NOTIFY_SOUND_APP -> "${CHANNEL_PREFIX}_app"
            SettingsStore.NOTIFY_SOUND_DRAGON -> "${CHANNEL_PREFIX}_dragon"
            SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_09 -> "${CHANNEL_PREFIX}_uf09"
            SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_062 -> "${CHANNEL_PREFIX}_uf062"
            else -> "${CHANNEL_PREFIX}_default"
        }
    }

    /** Uri for a picker selection, or null when "Default (system)" is chosen. */
    private fun soundUriFor(context: Context, selection: String): Uri? =
        when (selection) {
            SettingsStore.NOTIFY_SOUND_APP -> resourceUri(context, R.raw.notification_sound)
            SettingsStore.NOTIFY_SOUND_DRAGON -> resourceUri(context, R.raw.dragon_studio)
            SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_09 -> resourceUri(context, R.raw.universfield_09)
            SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_062 -> resourceUri(context, R.raw.universfield_062)
            else -> null
        }

    /** The user-selected receive sound, or the system default when "Default" is picked. */
    private fun selectedSoundUri(context: Context): Uri? =
        soundUriFor(context, (context.applicationContext as com.anindra.messages.MessagesApplication)
            .repository.settings.notificationSound)

    private fun resourceUri(context: Context, resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    /** Plays the given picker selection so the user hears it before confirming. */
    fun previewNotificationSound(context: Context, selection: String) {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
            val uri = soundUriFor(context, selection)
            if (uri != null) {
                val mp = android.media.MediaPlayer()
                mp.setDataSource(context, uri)
                mp.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setOnCompletionListener { previewPlayer?.release(); previewPlayer = null }
                mp.prepareAsync()
                mp.setOnPreparedListener { it.start() }
                previewPlayer = mp
            } else {
                android.media.RingtoneManager.getRingtone(
                    context,
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                )?.play()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * (Re)creates the notification channel using the system default notification
     * sound unless the user picked a bundled tone. When the "Receive sound"
     * setting is off the channel plays a bundled silent clip instead of null:
     * Android demotes sound-less channels to low importance and never heads-up
     * them, while a silent tone keeps IMPORTANCE_HIGH so the popup still appears.
     * createNotificationChannel() upserts in place, so this is safe to call on
     * every post and reflects live setting changes (the sound must be set
     * EXPLICITLY, or an update leaves a legacy custom tone in place).
     */
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val soundOn = app.repository.settings.receiveSoundEnabled
        val id = channelId(context)
        nm.createNotificationChannel(
            NotificationChannel(
                id, context.getString(R.string.notification_channel_title), NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                if (soundOn) {
                    setSound(
                        selectedSoundUri(context)
                            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                        android.app.Notification.AUDIO_ATTRIBUTES_DEFAULT
                    )
                } else {
                    setSound(resourceUri(context, R.raw.silent), android.app.Notification.AUDIO_ATTRIBUTES_DEFAULT)
                }
            }
        )
        // Drop the channels for the other selections so the system settings list
        // stays a single "Messages" entry.
        CHANNEL_VARIANTS.filter { it != id }.forEach { nm.deleteNotificationChannel(it) }
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
        // Skip notification if user is already reading this conversation
        if (ForegroundTracker.isAppInForeground && ForegroundTracker.isConversationOpen(from)) return

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

        // The reply action must be backed by a MUTABLE PendingIntent (RemoteInput
        // is silently dropped otherwise on Android 15+), so the wrapped Intent
        // carries an explicit component + package. It is built with plain
        // statements in this method (no helper, no apply-block) and the notify()
        // sink is inlined below, so CodeQL's intra-procedural explicit-intent
        // sanitizer can see the whole flow.
        val replyData = Intent(context, QuickReplyReceiver::class.java)
        replyData.action = QuickReplyReceiver.ACTION_REPLY
        replyData.setPackage(context.packageName)
        replyData.putExtra(QuickReplyReceiver.EXTRA_ADDRESS, from)
        replyData.putExtra(QuickReplyReceiver.EXTRA_FROM, from)
        replyData.putExtra(QuickReplyReceiver.EXTRA_NOTIF_ID, notifId)
        val replyIntent = PendingIntent.getBroadcast(
            context, reqCode,
            replyData,
            PendingIntent.FLAG_MUTABLE
        )

        val remoteInput = RemoteInput.Builder("quick_reply").setLabel("Reply").build()

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_reply, "Reply", replyIntent
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .addRemoteInput(remoteInput).build()

        val markReadData = Intent(context, MarkReadReceiver::class.java)
        markReadData.action = MarkReadReceiver.ACTION_MARK_READ
        markReadData.setPackage(context.packageName)
        markReadData.putExtra(MarkReadReceiver.EXTRA_ADDRESS, from)
        markReadData.putExtra(MarkReadReceiver.EXTRA_NOTIF_ID, notifId)
        val markReadIntent = PendingIntent.getBroadcast(
            context, reqCode + 1000,
            markReadData,
            PendingIntent.FLAG_IMMUTABLE
        )
        val markReadAction = NotificationCompat.Action.Builder(
            0, "Mark as read", markReadIntent
        ).build()

        val senderName = app.repository.contactNameFor(from) ?: from
        val title = if (privacyMode) context.getString(R.string.notif_title_private) else senderName
        val text = when {
            privacyMode -> context.getString(R.string.notif_body_private)
            app.repository.settings.hideLinks -> hideUrls(body)
            else -> body
        }

        // The channel carries the selected tone; the platform plays the channel
        // sound. The per-notification sound is kept in sync so status dumps and
        // any channel-less fallback agree. Note: never setSilent(true) here —
        // it groups the notification under "silent", which suppresses the
        // heads-up popup; the receive-sound-off case is handled by the channel
        // playing the bundled silent clip instead.
        val builder = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .addAction(replyAction)
            .addAction(markReadAction)
        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
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
        val failText = if (privacyMode) context.getString(R.string.notif_send_fail_private)
            else String.format(context.getString(R.string.notif_send_fail), to)

        val notif = NotificationCompat.Builder(context, channelId(context))
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(context.getString(R.string.notif_not_delivered))
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

    private fun manager(context: Context, subscriptionId: Int): android.telephony.SmsManager {
        val sm = context.getSystemService(android.telephony.SmsManager::class.java)
        if (subscriptionId == -1) return sm
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            sm.createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            android.telephony.SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
    }

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
        val sm = manager(context, subscriptionId)
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
        manager(context, subscriptionId).sendMultimediaMessage(context, media, null, null, sent)
        true
    } catch (_: Exception) {
        false
    }
}
