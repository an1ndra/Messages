package com.anindra.messages.sms

import com.anindra.messages.i18n.LocaleCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #441: a bubble kept the contact photo it was first published with. Three
 * caches held the picture: [ConversationAvatar]'s ten-minute icon cache, the
 * icon stored on the published shortcut, and the large icon already sitting in
 * the posted notification. Only the first was ever cleared, and only on a
 * cache miss, so a photo edit never reached the bubble.
 *
 * The fix fingerprints what the shortcut draws, keeps that fingerprint on the
 * shortcut itself, and republishes whenever it differs. These tests pin the
 * fingerprint and each of the three refresh steps against their source.
 */
class BubbleContactRefreshTest {

    private val app: File by lazy { File(LocaleCatalog.repoRoot(), "app/src/main") }

    private fun src(relative: String): String =
        File(app, "java/com/anindra/messages/$relative").readText()

    @Test
    fun theSignatureNamesTheDrawnPicture() {
        val signature = NotificationBubble.shortcutSignature("Ada", "PHOTO", 1234)
        assertEquals("Ada|PHOTO|4d2", signature)
        assertEquals(
            "the same picture must fingerprint the same way, or every post republishes",
            signature,
            NotificationBubble.shortcutSignature("Ada", "PHOTO", 1234)
        )
        assertNotEquals(
            "a photo replacing a monogram must not look unchanged",
            signature,
            NotificationBubble.shortcutSignature("Ada", "AVATAR", 1234)
        )
        assertNotEquals(
            "two different photos of the same contact must not collide",
            signature,
            NotificationBubble.shortcutSignature("Ada", "PHOTO", 1235)
        )
        assertNotEquals(
            "a renamed contact must refresh its label too",
            signature,
            NotificationBubble.shortcutSignature("Grace", "PHOTO", 1234)
        )
    }

    @Test
    fun thePublishedShortcutCarriesItsSignature() {
        val bubble = src("sms/NotificationBubble.kt")
        assertTrue(
            "the shortcut stores nothing about what it draws, so a contact edit is invisible",
            bubble.contains("putString(EXTRA_SIGNATURE, signature)")
        )
        assertTrue(
            "an unchanged picture still republishes, which the platform rate-limits",
            bubble.contains("published?.extras?.getString(EXTRA_SIGNATURE) == signature")
        )
        assertTrue(
            "the fingerprint does not include the pixels, so a replaced photo is indistinguishable",
            bubble.contains("shortcutSignature(name, icon.source.name, icon.pixels)")
        )
        assertTrue(
            "Diagnostics cannot name the picture a shortcut shows",
            bubble.contains("internal fun publishedShortcutIcons(")
        )
    }

    @Test
    fun aContactEditReachesEveryCache() {
        val support = src("sms/SmsSupport.kt")
        assertTrue(
            "the avatar cache is never cleared, so the old picture survives the TTL",
            support.contains("fun refreshContactAvatars(context: Context)") &&
                support.contains("ConversationAvatar.invalidate()")
        )
        assertTrue(
            "the notification is not reposted, so its large icon keeps the old photo",
            support.contains("repostUnread(context)")
        )
        assertTrue(
            "no shortcut is republished, so the bubble keeps the old icon",
            support.contains("NotificationBubble.ensureShortcut(context, id, address, name)")
        )

        val repository = src("data/Repository.kt")
        assertTrue(
            "the contacts observer does not ask for the published pictures to be rebuilt",
            repository.contains("NotificationHelper.refreshContactAvatars(context)")
        )
    }

    @Test
    fun diagnosticsNameThePictureEachShortcutShows() {
        val report = src("diagnostics/DiagnosticsReport.kt")
        assertTrue(report.contains("val bubbleIcons: List<String>"))
        assertTrue(report.contains("\"Bubble icons: "))
        assertTrue(
            "the report re-derives the icon instead of asking the system what it holds",
            report.contains("NotificationBubble.publishedShortcutIcons(context)")
        )
    }
}
