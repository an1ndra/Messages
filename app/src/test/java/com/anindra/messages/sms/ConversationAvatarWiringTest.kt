package com.anindra.messages.sms

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #354 follow-up: every conversation's bubble and notification showed the app's
 * launcher icon, because the bubble object drew `R.mipmap.ic_launcher` once and
 * reused it, and the poster built every `Person` on `R.drawable.ic_stat_message`.
 *
 * The picture now comes from one place — [ConversationAvatar] — so the bubble,
 * the shortcut, the panel avatar and the in-app list cannot drift apart.
 */
class ConversationAvatarWiringTest {

    private val app: File by lazy {
        File(LocaleCatalog.repoRoot(), "app/src/main")
    }

    private fun src(relative: String): String = File(app, "java/com/anindra/messages/$relative").readText()

    @Test
    fun theBubbleAndTheShortcutUseTheChataOwnPicture() {
        val bubble = src("sms/NotificationBubble.kt")
        assertFalse(
            "the bubble still paints the launcher icon for every conversation",
            bubble.contains("ic_launcher")
        )
        assertTrue(
            "the shortcut icon is not the conversation's own picture",
            bubble.contains("ConversationAvatar.forAddress(context, address).icon")
        )
        assertTrue(
            "the bubble metadata still uses a shared icon",
            bubble.contains("BubbleMetadata.Builder(") && bubble.contains("ConversationAvatar.forAddress")
        )
        assertFalse(
            "a process-wide icon cache is back, so every chat shares one picture",
            bubble.contains("cachedIcon")
        )
    }

    @Test
    fun theNotificationCarriesThePictureToo() {
        val poster = src("sms/SmsSupport.kt")
        assertTrue(
            "the notification has no large icon, which is the empty avatar the reporter saw",
            poster.contains("setLargeIcon(")
        )
        assertTrue(
            "the MessagingStyle sender is built without a picture",
            poster.contains("senderIcon = avatar?.icon")
        )
        assertTrue(
            "the added Person carries no icon",
            poster.contains("Person.Builder().setName(senderName).setKey(from).setImportant(true)")
        )
        assertTrue(
            "privacy mode would leak the contact through the avatar",
            poster.contains("val avatar = if (privacyMode) null else ConversationAvatar.forAddress")
        )
    }

    @Test
    fun theAvatarIsOnePaletteAndAnAdaptiveIcon() {
        val avatar = src("sms/ConversationAvatar.kt")
        assertTrue(
            "the notification avatar invents its own colour instead of the app's palette",
            avatar.contains("import com.anindra.messages.ui.avatarColor")
        )
        assertTrue(
            "the icon is a plain bitmap, so the platform cannot apply its own shape",
            avatar.contains("createWithAdaptiveBitmap")
        )
        assertTrue(
            "the fallback no longer matches the in-app silhouette",
            avatar.contains("ic_person_placeholder")
        )
        assertTrue(
            "a contact photo is never invalidated, so one added later never shows",
            avatar.contains("TTL_MS")
        )
    }
}
