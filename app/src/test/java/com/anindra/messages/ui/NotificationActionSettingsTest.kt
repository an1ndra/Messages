package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The notification's Reply, Mark as read and Delete actions are each opt-in from
 * Advanced settings (Notifications). Each action must be gated by its own
 * setting, and both UIs must expose the three toggles — the legacy UI keeps its
 * notification options inline in the Advanced screen, the new UI uses the
 * dedicated Notification settings screen.
 */
class NotificationActionSettingsTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun read(relative: String): String = File(main, relative).readText()

    @Test
    fun theBuilderGatesEachActionOnItsOwnSetting() {
        val sms = read("java/com/anindra/messages/sms/SmsSupport.kt")
        listOf("notifActionReply", "notifActionMarkRead", "notifActionDelete").forEach { setting ->
            assertTrue("SmsSupport never reads $setting", sms.contains(setting))
        }
    }

    @Test
    fun theSettingsStoreDefinesTheThreeKeys() {
        val store = read("java/com/anindra/messages/data/SettingsStore.kt")
        listOf(
            "KEY_NOTIF_ACTION_REPLY",
            "KEY_NOTIF_ACTION_MARK_READ",
            "KEY_NOTIF_ACTION_DELETE"
        ).forEach { key -> assertTrue("missing $key", store.contains(key)) }
        listOf("notifActionReply", "notifActionMarkRead", "notifActionDelete").forEach { prop ->
            assertTrue("missing property $prop", store.contains("var $prop:"))
        }
    }

    @Test
    fun bothUisExposeTheThreeToggles() {
        val newUi = read("java/com/anindra/messages/ui/AdvancedSubScreens.kt")
        val legacyUi = read("java/com/anindra/messages/ui/legacy/LegacyAdvancedSettingsScreen.kt")
        val titles = listOf(
            "settings_notif_action_reply_title",
            "settings_notif_action_mark_read_title",
            "settings_notif_action_delete_title"
        )
        listOf("new UI" to newUi, "legacy UI" to legacyUi).forEach { (label, src) ->
            titles.forEach { key ->
                assertTrue("$label: missing $key", src.contains("R.string.$key"))
            }
        }
    }
}
