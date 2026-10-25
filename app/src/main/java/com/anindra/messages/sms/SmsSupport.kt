package com.anindra.messages.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.anindra.messages.MainActivity
import com.anindra.messages.R
import com.anindra.messages.data.ConversationChannelTarget
import com.anindra.messages.data.SettingsStore
import com.anindra.messages.hideUrls
import kotlinx.coroutines.launch

object NotificationHelper {
    private var previewPlayer: android.media.MediaPlayer? = null

    /** How many recent conversations get a channel at launch, so the settings list stays readable. */
    private const val CHANNEL_SYNC_LIMIT = 100

    /**
     * The channel a message posts to: a conversation's own channel whenever there
     * is one, otherwise its category's. Single source of truth for the poster and
     * Diagnostics, so they cannot disagree.
     */
    internal fun channelIdFor(
        category: NotificationCategory,
        selection: String,
        soundOn: Boolean,
        conversationId: Long?,
        overrideSound: String
    ): String =
        NotificationChannels.forMessage(
            category, selection, soundOn, conversationId, overrideSound
        )

    private fun defaultSoundUri(): Uri? =
        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

    /** Uri for a picker selection, or null when "Default (system)" is chosen. */
    private fun soundUriFor(context: Context, selection: String): Uri? =
        when (selection) {
            SettingsStore.NOTIFY_SOUND_APP -> resourceUri(context, R.raw.notification_sound)
            SettingsStore.NOTIFY_SOUND_DRAGON -> resourceUri(context, R.raw.dragon_studio)
            SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_09 -> resourceUri(context, R.raw.universfield_09)
            SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_062 -> resourceUri(context, R.raw.universfield_062)
            else -> null
        }

    /**
     * The sound a channel plays for [selection]. When receive sound is off a
     * bundled silent clip is used rather than null: Android demotes sound-less
     * channels below IMPORTANCE_HIGH and never heads-up them, while a silent
     * tone keeps the popup working.
     */
    private fun channelSoundUri(context: Context, selection: String, soundOn: Boolean): Uri? =
        if (!soundOn) resourceUri(context, R.raw.silent)
        else soundUriFor(context, selection) ?: defaultSoundUri()

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
    /** Grouped per conversation, so expanding shows the recent messages from
     *  this sender rather than one long body. notify() replaces the record, so
     *  the history is re-read every time rather than appended to. */
    private fun groupedStyle(
        context: Context,
        app: com.anindra.messages.MessagesApplication,
        convoId: Long?,
        senderName: String,
        fallbackText: String,
        imageUri: String? = null,
        privacyMode: Boolean = false
    ): NotificationCompat.Style {
        val hideLinks = app.repository.settings.hideLinks
        val person = Person.Builder().setName(senderName)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_stat_message))
            .build()
        val me = Person.Builder().setName(context.getString(R.string.notif_you))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_stat_message))
            .build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(senderName)

        val history = convoId
            ?.let { app.repository.notificationHistory(it, NotificationHistory.MAX_LINES) }
            .orEmpty()
        // A picture-only MMS has a blank body: it gets a label instead of an
        // empty row, and the picture itself rides along as message data.
        val photoLabel = context.getString(R.string.notif_photo)
        val lines = NotificationHistory.window(
            if (history.isEmpty()) {
                listOf(
                    NotificationLine(
                        fallbackText, System.currentTimeMillis(), false,
                        isImage = imageUri != null, imageUri = imageUri
                    )
                )
            } else history.map {
                NotificationLine(
                    if (hideLinks) hideUrls(it.text) else it.text,
                    it.timestamp,
                    fromMe = false,
                    isImage = it.isImage,
                    imageUri = it.imageUri
                )
            }
        )
        // Only the newest picture is prepared: copying every one would be work
        // on a receiver thread for lines that sit below the visible one.
        val newestImage = lines.lastOrNull { it.isImage }
        lines.forEach {
            val text = if (it.text.isBlank() && it.isImage) photoLabel else it.text
            val message = NotificationCompat.MessagingStyle.Message(
                text, it.timestamp, if (it.fromMe) me else person
            )
            if (!privacyMode && it === newestImage && it.imageUri != null) {
                NotificationImage.prepare(context, it.imageUri)?.let { uri ->
                    message.setData(NotificationImage.MIME, uri)
                }
            }
            style.addMessage(message)
        }
        return style
    }

    /**
     * (Re)creates the per-category channels. Distinct channels are what put
     * sound/vibration/importance knobs in system settings per category; the app
     * can only set their defaults, and the user owns them afterwards.
     *
     * Safe to call on every post and reflects live setting changes (the sound
     * must be set EXPLICITLY, or an update leaves a legacy custom tone in
     * place). The single pre-category channels are deleted so the list does not
     * double up.
     */
    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val settings = app.repository.settings
        val soundOn = settings.receiveSoundEnabled
        NotificationCategory.entries.forEach { category ->
            val selection = settings.notificationSoundFor(NotificationCategory.keyOf(category))
            val id = NotificationChannels.forCategory(category, selection, soundOn)
            recoverDemotedChannel(nm, id)
            nm.createNotificationChannel(
                NotificationChannel(
                    id,
                    context.getString(NotificationChannels.titleRes(category)),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    setSound(
                        channelSoundUri(context, selection, soundOn),
                        android.app.Notification.AUDIO_ATTRIBUTES_DEFAULT
                    )
                }
            )
            // A channel's sound is fixed once it exists, so the previous
            // selection's channel is switched away from, not edited.
            NotificationChannels.categoryVariants(category)
                .filter { it != id }
                .forEach { nm.deleteNotificationChannel(it) }
        }
        NotificationChannels.LEGACY_VARIANTS.forEach { nm.deleteNotificationChannel(it) }
    }

    /**
     * Creates or refreshes a conversation's channel (#339) and returns its id.
     * Every conversation owns one, titled after the chat, because a per-chat row
     * in system settings is the only way Android offers per-chat sound,
     * vibration and importance.
     *
     * The id carries the tone, so changing it lands on a fresh id and the
     * conversation's other variants are deleted: an existing channel will not
     * take a new sound.
     */
    internal fun ensureConversationChannel(
        context: Context,
        target: ConversationChannelTarget,
        category: NotificationCategory,
        soundOn: Boolean
    ): String {
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val selection = app.repository.settings.notificationSoundFor(NotificationCategory.keyOf(category))
        val sound = target.overrideSound.ifEmpty { selection }
        val id = channelIdFor(category, selection, soundOn, target.conversationId, target.overrideSound)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return id
        val label = channelLabel(target)
        val desired = channelSoundUri(context, sound, soundOn)
        val existing = nm.getNotificationChannel(id)
        if (existing != null && existing.sound == desired && existing.name == label) return id
        if (existing == null) {
            // A fresh tone (or the first post): the previous variant is stale.
            // Checking the desired id first keeps a launch sync to one binder
            // read per conversation instead of six writes.
            NotificationChannels.conversationVariants(target.conversationId)
                .filter { it != id }
                .forEach { nm.deleteNotificationChannel(it) }
        } else if (existing.sound != desired) {
            nm.deleteNotificationChannel(id)
        }
        nm.createNotificationChannel(
            NotificationChannel(id, label, NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notification_channel_convo_desc)
                setSound(desired, android.app.Notification.AUDIO_ATTRIBUTES_DEFAULT)
            }
        )
        return id
    }

    /** The row Android shows in system settings for a conversation. */
    internal fun channelLabel(target: ConversationChannelTarget): String =
        com.anindra.messages.ui.ContactDetails.listLabel(
            target.groupTitle, target.name, target.address, target.display
        )

    /**
     * The category a conversation belongs to. One decision shared by the poster
     * and both channel syncs, so a chat cannot inherit two different tones.
     */
    internal fun categoryFor(target: ConversationChannelTarget): NotificationCategory =
        NotificationCategory.resolve(
            isGroup = target.recipients > 1,
            knownContact = com.anindra.messages.ui.ContactDetails.isKnown(
                target.name, target.address
            ),
            replyable = com.anindra.messages.data.AddressIdentity.isReplyable(target.address)
        )

    /** Deletes every tone variant of a conversation's channel. */
    fun deleteConversationChannels(context: Context, conversationId: Long) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        NotificationChannels.conversationVariants(conversationId)
            .forEach { nm.deleteNotificationChannel(it) }
    }

    /**
     * Gives the most recent conversations their channel at launch, so system
     * settings lists chats nothing has arrived in yet, and prunes the channels
     * of conversations that are gone.
     */
    fun syncConversationChannels(context: Context) {
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val repo = app.repository
        val soundOn = repo.settings.receiveSoundEnabled
        repo.recentConversationChannelTargetsBlocking(CHANNEL_SYNC_LIMIT).forEach { target ->
            ensureConversationChannel(context, target, categoryFor(target), soundOn)
        }
        pruneConversationChannels(context)
    }

    /** Drops the channels of conversations that no longer exist. */
    fun pruneConversationChannels(context: Context) {
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val live = app.repository.liveConversationIdsBlocking()
        context.getSystemService(NotificationManager::class.java)
            ?.notificationChannels
            ?.mapNotNull { NotificationChannels.conversationIdOf(it.id) }
            ?.distinct()
            ?.filter { it !in live }
            ?.forEach { deleteConversationChannels(context, it) }
    }

    /**
     * Recreates the channels that already exist after a sound or receive-sound
     * change, and drops any whose conversation is gone. Only conversations that
     * own a channel are touched, so a long history does not dump hundreds of
     * rows into system settings at once.
     */
    fun syncNotificationChannels(context: Context) {
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val repo = app.repository
        val soundOn = repo.settings.receiveSoundEnabled
        context.getSystemService(NotificationManager::class.java)
            ?.notificationChannels
            ?.mapNotNull { NotificationChannels.conversationIdOf(it.id) }
            ?.distinct()
            ?.forEach { conversationId ->
                val target = repo.conversationChannelTargetBlocking(conversationId)
                if (target == null) deleteConversationChannels(context, conversationId)
                else ensureConversationChannel(context, target, categoryFor(target), soundOn)
            }
    }

    /** Runs [syncNotificationChannels] off the main thread, for settings rows. */
    fun reconcileAsync(context: Context) {
        val app = context.applicationContext
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        ).launch { syncNotificationChannels(app) }
    }

    /**
     * createNotificationChannel() upserts, and an upsert cannot raise importance
     * once the user has touched the channel — so a demotion is permanent. The
     * only recovery is delete-then-recreate. IMPORTANCE_DEFAULT is left alone:
     * a user who deliberately quieted the app keeps their choice, and only a
     * channel that has fallen below it (the broken state) is rebuilt.
     */
    private fun recoverDemotedChannel(nm: NotificationManager, id: String) {
        val existing = nm.getNotificationChannel(id) ?: return
        if (existing.importance >= NotificationManager.IMPORTANCE_DEFAULT) return
        nm.deleteNotificationChannel(id)
    }

    /** Whether the user has allowed bubbles for the app, read from the platform. */
    internal fun bubblesAllowed(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.areBubblesAllowed() == true

    /** Channel ids whose live settings allow bubbles, for Diagnostics. */
    internal fun bubbleChannels(context: Context): List<String> =
        context.getSystemService(NotificationManager::class.java)
            ?.notificationChannels
            ?.filter { it.canBubble() }
            ?.map { it.id }
            ?.sorted()
            .orEmpty()

    /** Live importance of a specific channel, or IMPORTANCE_NONE when absent. */
    internal fun channelImportanceFor(context: Context, channelId: String): Int =
        context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(channelId)
            ?.importance
            ?: NotificationManager.IMPORTANCE_NONE

    /** The direct channel's importance, the representative for wake eligibility. */
    internal fun channelImportance(context: Context): Int {
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val settings = app.repository.settings
        val id = NotificationChannels.forCategory(
            NotificationCategory.DIRECT,
            settings.notificationSoundFor(NotificationCategory.keyOf(NotificationCategory.DIRECT)),
            settings.receiveSoundEnabled
        )
        return channelImportanceFor(context, id)
    }

    /** Every channel this app has created, id to importance, for Diagnostics. */
    internal fun createdChannels(context: Context): List<Pair<String, Int>> =
        context.getSystemService(NotificationManager::class.java)
            ?.notificationChannels
            ?.map { it.id to it.importance }
            ?.sortedBy { it.first }
            .orEmpty()

    private fun canPost(context: Context): Boolean {
        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        if (!app.repository.settings.notificationsEnabled) return false
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return true
    }

    fun show(context: Context, from: String, body: String, imageUri: String? = null) {
        // Skip notification if user is already reading this conversation
        if (NotificationPolicy.skipForOpenThread(
                ForegroundTracker.isAppInForeground,
                ForegroundTracker.isConversationOpen(from)
            )
        ) return

        // create the channels before any early-return: notify() with an unknown
        // channel id is a silent no-op, so the first-ever post must have it ready
        ensureChannels(context)
        if (!canPost(context)) return

        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val convoId = app.repository.conversationIdForAddress(from)
        if (convoId != null && !app.repository.getConversationNotificationsEnabledBlocking(convoId)) return

        val replyable = com.anindra.messages.data.AddressIdentity.isReplyable(from)
        val contactName = app.repository.contactNameFor(from)
        val settings = app.repository.settings
        // A conversation always posts on its own channel (#339); the category
        // channel is only the fallback for a sender with no conversation row.
        val target = convoId?.let { app.repository.conversationChannelTargetBlocking(it) }
        val category = target?.let { categoryFor(it) }
            ?: NotificationCategory.resolve(
                isGroup = false, knownContact = contactName != null, replyable = replyable
            )
        val channelId = if (target != null) {
            ensureConversationChannel(context, target, category, settings.receiveSoundEnabled)
        } else {
            channelIdFor(
                category,
                settings.notificationSoundFor(NotificationCategory.keyOf(category)),
                settings.receiveSoundEnabled,
                null,
                ""
            )
        }

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

        // Alphanumeric sender IDs cannot receive replies, so no reply action.
        val replyAction = if (replyable) {
            NotificationCompat.Action.Builder(
                R.drawable.ic_reply, "Reply", replyIntent
            ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .addRemoteInput(remoteInput).build()
        } else {
            null
        }

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

        val deleteData = Intent(context, DeleteMessageReceiver::class.java)
        deleteData.action = DeleteMessageReceiver.ACTION_DELETE
        deleteData.setPackage(context.packageName)
        deleteData.putExtra(DeleteMessageReceiver.EXTRA_ADDRESS, from)
        deleteData.putExtra(DeleteMessageReceiver.EXTRA_NOTIF_ID, notifId)
        val deleteIntent = PendingIntent.getBroadcast(
            context, reqCode + 2000,
            deleteData,
            PendingIntent.FLAG_IMMUTABLE
        )
        val deleteAction = NotificationCompat.Action.Builder(
            0, context.getString(R.string.notif_action_delete), deleteIntent
        ).build()

        val senderName = app.repository.contactNameFor(from) ?: from
        val title = if (privacyMode) context.getString(R.string.notif_title_private) else senderName
        val text = when {
            privacyMode -> context.getString(R.string.notif_body_private)
            // A picture-only MMS has no text; an empty text is what made the
            // whole notification blank.
            body.isBlank() && imageUri != null -> context.getString(R.string.notif_photo)
            app.repository.settings.hideLinks -> hideUrls(body)
            else -> body
        }

        // A conversation bubble needs a published long-lived shortcut; when one
        // cannot be published (shortcut updates are rate limited) the same
        // notification still posts, just without a bubble.
        val bubbleConversationId = convoId?.takeIf { NotificationBubble.shouldAttach(it, replyable) }
        val bubbleReady = bubbleConversationId != null &&
            NotificationBubble.ensureShortcut(context, bubbleConversationId, from, senderName)

        // The channel carries the selected tone; the platform plays the channel
        // sound. The per-notification sound is kept in sync so status dumps and
        // any channel-less fallback agree. Note: never setSilent(true) here —
        // it groups the notification under "silent", which suppresses the
        // heads-up popup; the receive-sound-off case is handled by the channel
        // playing the bundled silent clip instead.
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            // Launchers that render a count read this field, and aggregate it
            // across the app's live notifications.
            .setNumber(BadgePolicy.badgeCount(app.repository.unreadCountFor(convoId)))
            .setContentIntent(tap)
            .setStyle(groupedStyle(context, app, convoId, senderName, text, imageUri, privacyMode))
        if (bubbleConversationId != null && bubbleReady) {
            try {
                builder.setShortcutId(NotificationBubble.shortcutId(bubbleConversationId))
                builder.addPerson(
                    Person.Builder().setName(senderName).setKey(from).setImportant(true).build()
                )
                builder.setBubbleMetadata(
                    NotificationBubble.metadata(context, from, bubbleConversationId, reqCode + 4000)
                )
            } catch (e: Exception) {
                android.util.Log.w("NotificationBubble", "attach conversation $bubbleConversationId: $e")
            }
        }
        // A heads-up popup is a peek: on a locked screen the panel stays asleep.
        // Only a full-screen intent is treated as a user-initiated wake, so this
        // is what turns the screen on. Privacy mode suppresses it because the
        // trampoline would otherwise surface content over the keyguard.
        if (shouldWake(app.repository.settings, keyguardLocked(context))) {
            builder.setFullScreenIntent(fullScreenIntent(context, reqCode), true)
        }

        val actionSettings = app.repository.settings
        if (actionSettings.notifActionReply && replyAction != null) builder.addAction(replyAction)
        if (actionSettings.notifActionMarkRead) builder.addAction(markReadAction)
        if (actionSettings.notifActionDelete) builder.addAction(deleteAction)
        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    /**
     * Whether an incoming message should carry a full-screen intent and wake
     * the device. Pure so it can be unit-tested without a device.
     */
    internal fun shouldWake(
        keyguardLocked: Boolean,
        notificationsEnabled: Boolean,
        receiveSoundEnabled: Boolean
    ): Boolean =
        keyguardLocked && notificationsEnabled && receiveSoundEnabled

    /** Same policy, reading the three inputs off the settings store. */
    internal fun shouldWake(settings: SettingsStore, keyguardLocked: Boolean): Boolean =
        shouldWake(
            keyguardLocked = keyguardLocked,
            notificationsEnabled = settings.notificationsEnabled,
            receiveSoundEnabled = settings.receiveSoundEnabled
        )

    private fun keyguardLocked(context: Context): Boolean =
        context.getSystemService(android.app.KeyguardManager::class.java)
            ?.isKeyguardLocked == true

    /**
     * Target for the full-screen intent. A dedicated trampoline rather than
     * MainActivity: the platform FSI policy requires the activity not be the
     * app's primary launch target, and a real lock screen takeover is not the
     * same as cold-launching the app.
     */
    private fun fullScreenIntent(context: Context, reqCode: Int): PendingIntent {
        val data = Intent(context, FullScreenSmsActivity::class.java)
        data.action = FullScreenSmsActivity.ACTION_SHOW
        data.setPackage(context.packageName)
        return PendingIntent.getActivity(context, reqCode + 3000, data, PendingIntent.FLAG_IMMUTABLE)
    }

    /** Posted when an outgoing SMS/MMS fails to hand off to the radio. */
    fun showSendFailed(context: Context, to: String) {
        ensureChannels(context)
        if (!canPost(context)) return

        val app = context.applicationContext as com.anindra.messages.MessagesApplication
        val convoId = app.repository.conversationIdForAddress(to)
        val settings = app.repository.settings
        // A failure belongs to the same conversation channel its messages use.
        val target = convoId?.let { app.repository.conversationChannelTargetBlocking(it) }
        val category = target?.let { categoryFor(it) }
            ?: NotificationCategory.resolve(
                isGroup = false,
                knownContact = app.repository.contactNameFor(to) != null,
                replyable = com.anindra.messages.data.AddressIdentity.isReplyable(to)
            )
        val channelId = if (target != null) {
            ensureConversationChannel(context, target, category, settings.receiveSoundEnabled)
        } else {
            channelIdFor(
                category,
                settings.notificationSoundFor(NotificationCategory.keyOf(category)),
                settings.receiveSoundEnabled,
                null,
                ""
            )
        }
        val failId = (convoId ?: to.hashCode().toLong()).toInt()

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

        val notif = NotificationCompat.Builder(context, channelId)
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

    fun clearConversationNotification(
        context: Context,
        conversationId: Long?,
        address: String? = null
    ) {
        // Cancelling a notification cancels its bubble, and reading the thread
        // is exactly what opens a bubble. A live bubble host owns the
        // notification and dismisses it when the bubble closes.
        if (BubbleHost.holds(conversationId)) return
        val ids = BadgePolicy.idsToDismiss(conversationId, address?.hashCode()) ?: return
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(ids.first)
        nm.cancel("failed", ids.second)
    }

    /** A sender moving to Spam & blocked must drop out of the launcher badge:
     *  the badge sums the numbers of the app's live notifications, and this
     *  sender's notification keeps its unread number until it is cancelled —
     *  the messages it covered stay unread in the blocked folder, and the
     *  ones that follow a block are never posted to replace it (#345). A
     *  notification a live bubble host owns is left alone, like above. */
    fun clearBlockedConversationNotifications(
        context: Context,
        conversationIds: List<Long>,
        address: String
    ) {
        // One of the entries can be the address-hash id show() posts when the
        // sender had no conversation row; a held bubble is always identified
        // by its conversation id.
        val held = conversationIds.filter { BubbleHost.holds(it) }.map { it.toInt() }.toSet()
        val nm = NotificationManagerCompat.from(context)
        for (id in BadgePolicy.idsToDismissOnBlock(conversationIds, address)) {
            if (id in held) continue
            nm.cancel(id)
            nm.cancel("failed", id)
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

    internal fun manager(context: Context, subscriptionId: Int): android.telephony.SmsManager {
        val sm = context.getSystemService(android.telephony.SmsManager::class.java)
        if (subscriptionId == -1) return sm
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            sm.createForSubscriptionId(subscriptionId)
        } else {
            legacyManagerForSubscription(subscriptionId)
        }
    }

    // getSmsManagerForSubscriptionId is the only per-SIM API on 29-30 and is
    // deprecated on S+; reflect to avoid compiling against the deprecated call.
    private fun legacyManagerForSubscription(subscriptionId: Int): android.telephony.SmsManager =
        android.telephony.SmsManager::class.java
            .getMethod("getSmsManagerForSubscriptionId", Int::class.javaPrimitiveType)
            .invoke(null, subscriptionId) as android.telephony.SmsManager

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

    /**
     * A one-off SMS with no stored row and no status callback, for the reaction
     * fallback (issue #188): the recipient should see a plain text, but the
     * sending side must not show it as a message in its own chat.
     */
    fun sendRaw(
        context: Context,
        address: String,
        body: String,
        subscriptionId: Int = -1
    ): Boolean = try {
        val sm = manager(context, subscriptionId)
        val dest = normalizeAddress(address)
        val parts = sm.divideMessage(body)
        if (parts.size <= 1) {
            sm.sendTextMessage(dest, null, body, null, null)
        } else {
            sm.sendMultipartTextMessage(dest, null, parts, null, null)
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Strips formatting from a stored phone address; keeps digits and a
     *  leading '+'. Non-numeric (alphanumeric sender ID) addresses are returned
     *  trimmed rather than reduced to their digits (issue #207). */
    private fun normalizeAddress(address: String): String {
        val digits = address.filter { it.isDigit() }
        if (digits.isEmpty() || address.any { it.isLetter() }) return address.trim()
        return if (address.trimStart().startsWith("+")) "+$digits" else digits
    }

    /**
     * Sends MMS through the `:mms` stack (see [MmsSender]).
     *
     * The stack fits the attachment to the carrier, builds the PDU, writes the
     * outbox row and hands it over; SmsStatusReceiver settles the app row when
     * the platform reports the result.
     */
    fun sendMms(
        context: Context,
        messageId: Long,
        addresses: List<String>,
        media: Uri,
        subscriptionId: Int = -1,
        caption: String = ""
    ): Boolean {
        val mime = com.anindra.messages.data.MmsSupport.defaultAttachmentMime(
            context.contentResolver.getType(media), media.toString()
        )
        return MmsSender.send(
            context, messageId, addresses, media, mime, caption, subscriptionId
        )
    }

}
