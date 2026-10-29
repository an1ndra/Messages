package com.anindra.messages.ui

import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #386: "Can't Schedule Texts After Installation" — the app shipped a
 * Scheduled messages section and a settings toggle, but the only way to create
 * one was a long-press on Send, and the toggle was written by the legacy screen
 * and read nowhere, so it changed nothing.
 *
 * The composer now shows a Schedule control next to Send whenever there is
 * something to send, the long-press remains as a shortcut, and the preference
 * gates both — so the setting is not decorative.
 */
class ScheduleAffordanceTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val chat get() = read("src/main/java/com/anindra/messages/ui/ChatScreen.kt")
    private val advanced get() = read("src/main/java/com/anindra/messages/ui/AdvancedSettingsScreen.kt")
    private val store get() = read("src/main/java/com/anindra/messages/data/SettingsStore.kt")

    @Test
    fun theComposerOffersAVisibleScheduleControl() {
        assertTrue(
            "the composer must draw a schedule affordance, not only a long-press",
            chat.contains("contentDescription = scheduleLabel") &&
                chat.contains("Icons.Rounded.Schedule")
        )
        assertTrue(
            "and it must be labelled for screen readers",
            chat.contains("val scheduleLabel = stringResource(R.string.chat_schedule)")
        )
        assertTrue(
            "tapping it opens the picker",
            Regex("""combinedClickable\(onClick = onSchedule\)""").containsMatchIn(chat)
        )
    }

    @Test
    fun longPressStaysButOnlyWhenSchedulingIsOn() {
        assertTrue(
            "an inert long-press when the feature is off would be a dead control",
            chat.contains("onLongClick = if (schedulingEnabled) onSchedule else null")
        )
    }

    @Test
    fun theChatPassesTheStoredPreference() {
        assertTrue(
            "the composer must read the setting, or it stays decorative",
            chat.contains("schedulingEnabled = vm.settings.scheduledMessagesEnabled")
        )
    }

    @Test
    fun theNewSettingsUiCanTurnItOff() {
        assertTrue(
            "the toggle was legacy-only, so the new UI could not change it",
            advanced.contains("vm.settings.scheduledMessagesEnabled = it")
        )
    }

    @Test
    fun thePreferenceStillDefaultsToOn() {
        assertTrue(
            "scheduling must not be off for a fresh install",
            store.contains("prefs.getBoolean(KEY_SCHEDULED_MESSAGES_ENABLED, true)")
        )
    }

    @Test
    fun everyTranslationHasTheScheduleLabel() {
        val locales = listOf(
            "", "-ar", "-de", "-es", "-fr", "-hi", "-ja", "-ko",
            "-pl", "-pt-rBR", "-ru", "-zh-rCN", "-zh-rTW"
        )
        locales.forEach { locale ->
            assertTrue(
                "values$locale is missing chat_schedule",
                read("src/main/res/values$locale/strings_chat.xml").contains("name=\"chat_schedule\"")
            )
        }
    }
}
