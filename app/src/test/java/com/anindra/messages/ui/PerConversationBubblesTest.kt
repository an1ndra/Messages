package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A conversation can opt out of bubbles on its own, the way Google Messages
 * offers a per-conversation bubble control; the platform's bubbles page stays
 * the app-wide switch.
 */
class PerConversationBubblesTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val details get() = read("src/main/java/com/anindra/messages/ui/ContactDetailsScreen.kt")
    private val support get() = read("src/main/java/com/anindra/messages/sms/SmsSupport.kt")
    private val store get() = read("src/main/java/com/anindra/messages/data/SettingsStore.kt")

    @Test
    fun theConversationScreenHasTheSwitch() {
        assertTrue(
            "the details screen must offer the toggle",
            details.contains("stringResource(R.string.contact_bubble_conversation)")
        )
        assertTrue(
            "and write it back for this conversation",
            details.contains("vm.settings.setBubblesFor(conversationId, it)")
        )
    }

    @Test
    fun itDefaultsToOn() {
        assertTrue(
            "every conversation bubbles unless the user says otherwise",
            store.contains("!prefs.getBoolean(KEY_BUBBLE_OFF_PREFIX + conversationId, false)")
        )
    }

    @Test
    fun theNotificationPathHonoursIt() {
        assertTrue(
            "bubble metadata must be gated on the conversation's own choice",
            support.contains("app.repository.settings.bubblesFor(it) &&")
        )
    }

    @Test
    fun everyTranslationHasTheLabel() {
        val locales = listOf(
            "", "-ar", "-de", "-es", "-fr", "-hi", "-ja", "-ko",
            "-pl", "-pt-rBR", "-ru", "-zh-rCN", "-zh-rTW"
        )
        locales.forEach { locale ->
            assertTrue(
                "values$locale is missing contact_bubble_conversation",
                read("src/main/res/values$locale/strings_settings.xml")
                    .contains("name=\"contact_bubble_conversation\"")
            )
        }
    }
}
