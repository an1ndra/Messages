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

/**
 * Turns a conversation's notification into a conversation bubble (#354).
 *
 * Android only floats a notification as a bubble when it references a
 * published, long-lived shortcut, so the poster asks this object for both the
 * shortcut id and the bubble metadata. Publishing is rate limited by the
 * platform (10 per interval on the AOSP build), so a shortcut is only pushed
 * when it is missing or the picture it shows has changed; a bubble that cannot
 * be published must still produce a working notification.
 */
object NotificationBubble {

    /** Extra on the bubble launch intent; the bubble host must not dismiss its own notification. */
    const val EXTRA_OPEN_IN_BUBBLE = "open_in_bubble"

    /** Label the published shortcuts carry, so Diagnostics can tell them from other app shortcuts. */
    internal const val CATEGORY = "com.anindra.messages.category.CONVERSATION"

    /** What a published shortcut currently shows, kept on the shortcut itself. */
    private const val EXTRA_SIGNATURE = "conversation_icon"

    private const val BUBBLE_HEIGHT_DP = 600

    /** A bubble needs a conversation id to key the shortcut on, and a replyable sender. */
    internal fun shouldAttach(conversationId: Long?, replyable: Boolean): Boolean =
        conversationId != null && replyable

    internal fun shortcutId(conversationId: Long): String = "conversation_$conversationId"

    /**
     * What the shortcut for this chat should look like. Storing it on the
     * shortcut is what lets a post tell an edited contact (new picture or
     * name) from the one the platform already holds (#441); the signature is
     * the drawn picture's fingerprint, so two different photos never collide.
     */
    internal fun shortcutSignature(name: String, source: String, pixels: Int): String =
        "$name|$source|${Integer.toHexString(pixels)}"

    private fun publishedShortcuts(context: Context): List<ShortcutInfoCompat> =
        ShortcutManagerCompat.getShortcuts(
            context,
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED
        ).filter { it.categories?.contains(CATEGORY) == true }

    /** Every conversation shortcut the platform currently holds, for Diagnostics. */
    internal fun publishedShortcutIds(context: Context): List<String> =
        publishedShortcuts(context).map { it.id }.sorted()

    /**
     * The picture each published shortcut shows, for Diagnostics: a contact
     * photo edit is otherwise only visible in a masked pixel, which no
     * regression script can read.
     */
    internal fun publishedShortcutIcons(context: Context): List<String> =
        publishedShortcuts(context)
            .sortedBy { it.id }
            .map { "${it.id} ${it.extras?.getString(EXTRA_SIGNATURE) ?: "unpublished"}" }

    /**
     * Publishes or refreshes the conversation's shortcut. Returns whether the
     * shortcut exists afterwards, which is what decides if the bubble metadata
     * is worth attaching.
     */
    internal fun ensureShortcut(
        context: Context,
        conversationId: Long,
        address: String,
        name: String
    ): Boolean {
        val id = shortcutId(conversationId)
        return try {
            val icon = ConversationAvatar.forAddress(context, address)
            val signature = shortcutSignature(name, icon.source.name, icon.pixels)
            val published = publishedShortcuts(context).firstOrNull { it.id == id }
            if (published?.extras?.getString(EXTRA_SIGNATURE) == signature) {
                true
            } else if (ShortcutManagerCompat.isRateLimitingActive(context)) {
                // The old shortcut still floats a working bubble; the picture
                // catches up on the next post that the platform allows.
                published != null
            } else {
                val shortcut = ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel(name)
                    // The chat's own picture, not the app's: the bubble and the
                    // shortcut both show this, and one launcher icon for every
                    // conversation is what the reporter saw (#354 follow-up).
                    .setIcon(icon.icon)
                    .setPerson(
                        Person.Builder().setName(name).setKey(address).setImportant(true).build()
                    )
                    .setLongLived(true)
                    .setCategories(setOf(CATEGORY))
                    .setExtras(
                        android.os.PersistableBundle().apply {
                            putString(EXTRA_SIGNATURE, signature)
                        }
                    )
                    .setIntent(launchIntent(context, address, conversationId))
                    .build()
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
                android.util.Log.d("NotificationBubble", "shortcut $id icon=$signature")
                true
            }
        } catch (e: Exception) {
            // A bubble is a nicety; losing the message notification over one is
            // not acceptable, so every failure degrades to an ordinary
            // notification. An existing shortcut still floats its bubble.
            android.util.Log.w("NotificationBubble", "conversation $conversationId: $e")
            publishedShortcutIds(context).contains(id)
        }
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
     *
     * NEW_DOCUMENT + MULTIPLE_TASK is what makes the bubble own a task. Without
     * them MainActivity's singleTop handed the launch to the instance already on
     * screen: the app navigated and the expanded bubble had nothing to host, so
     * it showed blank (#354). These are the flags Google Messages' own bubble
     * launch carries.
     */
    private fun launchIntent(context: Context, address: String, conversationId: Long): Intent =
        Intent(context, com.anindra.messages.BubbleChatActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("messages://conversation/$conversationId")
            setPackage(context.packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            )
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
