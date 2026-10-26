package com.anindra.messages.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.anindra.messages.MainActivity

/**
 * Turns a conversation's notification into a conversation bubble (#354).
 *
 * Android only floats a notification as a bubble when it references a
 * published, long-lived shortcut, so the poster asks this object for both the
 * shortcut id and the bubble metadata. The shortcut is published at most once
 * per conversation: shortcut updates are rate limited by the platform (10 per
 * interval on the AOSP build), and a bubble that cannot be published must still
 * produce a working notification.
 */
object NotificationBubble {

    /** Extra on the bubble launch intent; the bubble host must not dismiss its own notification. */
    const val EXTRA_OPEN_IN_BUBBLE = "open_in_bubble"

    /** Label the published shortcuts carry, so Diagnostics can tell them from other app shortcuts. */
    internal const val CATEGORY = "com.anindra.messages.category.CONVERSATION"

    private const val BUBBLE_HEIGHT_DP = 600

    /** A bubble needs a conversation id to key the shortcut on, and a replyable sender. */
    internal fun shouldAttach(conversationId: Long?, replyable: Boolean): Boolean =
        conversationId != null && replyable

    internal fun shortcutId(conversationId: Long): String = "conversation_$conversationId"

    /** Every conversation shortcut the platform currently holds, for Diagnostics. */
    internal fun publishedShortcutIds(context: Context): List<String> =
        ShortcutManagerCompat.getShortcuts(
            context,
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED
        ).filter { it.categories?.contains(CATEGORY) == true }
            .map { it.id }
            .sorted()

    /**
     * Publishes the conversation's shortcut if it is missing. Returns whether
     * the shortcut exists afterwards, which is what decides if the bubble
     * metadata is worth attaching.
     */
    internal fun ensureShortcut(
        context: Context,
        conversationId: Long,
        address: String,
        name: String
    ): Boolean = try {
        val id = shortcutId(conversationId)
        if (publishedShortcutIds(context).contains(id)) {
            true
        } else if (ShortcutManagerCompat.isRateLimitingActive(context)) {
            false
        } else {
            val shortcut = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(name)
                // The chat's own picture, not the app's: the bubble and the
                // shortcut both show this, and one launcher icon for every
                // conversation is what the reporter saw (#354 follow-up).
                .setIcon(ConversationAvatar.forAddress(context, address).icon)
                .setPerson(
                    Person.Builder().setName(name).setKey(address).setImportant(true).build()
                )
                .setLongLived(true)
                .setCategories(setOf(CATEGORY))
                .setIntent(launchIntent(context, address, conversationId))
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    } catch (e: Exception) {
        // A bubble is a nicety; losing the message notification over one is not
        // acceptable, so every failure degrades to an ordinary notification.
        android.util.Log.w("NotificationBubble", "conversation $conversationId: $e")
        false
    }

    /** Bubble metadata pointing at the same conversation the notification taps into. */
    internal fun metadata(
        context: Context,
        address: String,
        conversationId: Long,
        requestCode: Int
    ): NotificationCompat.BubbleMetadata {
        // The platform rejects an immutable PendingIntent on bubble metadata.
        // The intent is explicit (component + package, per-conversation data),
        // which is what keeps the mutable flag safe.
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent(context, address, conversationId),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.BubbleMetadata.Builder(
            pending, ConversationAvatar.forAddress(context, address).icon
        )
            .setDesiredHeight(BUBBLE_HEIGHT_DP)
            .setAutoExpandBubble(false)
            .setSuppressNotification(false)
            .build()
    }

    /**
     * The data URI is what keeps the bubble PendingIntents apart: PendingIntent
     * equality ignores extras, so two conversations sharing a request code would
     * otherwise open each other's thread.
     */
    private fun launchIntent(context: Context, address: String, conversationId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("messages://conversation/$conversationId")
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("open_conversation_address", address)
            putExtra(EXTRA_OPEN_IN_BUBBLE, true)
        }
}

/**
 * The conversations a live bubble host is showing. Cancelling a notification
 * cancels its bubble, and the chat dismisses its notification as soon as the
 * thread is read, so that dismissal waits until the bubble itself closes.
 */
internal object BubbleHost {
    private val conversations = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    fun enter(conversationId: Long) {
        conversations.add(conversationId)
    }

    fun leave(conversationId: Long) {
        conversations.remove(conversationId)
    }

    fun holds(conversationId: Long?): Boolean =
        conversationId != null && conversations.contains(conversationId)
}
