package com.anindra.messages.sms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wiring for the per-category channels (#339): the schema column, the settings
 * store, the poster and Diagnostics all have to agree, so each side is pinned
 * against the source that owns it.
 */
class NotificationChannelWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private fun read(relative: String): String = File(main, relative).readText()

    private fun src(name: String) = read("java/com/anindra/messages/$name")

    @Test
    fun posterResolvesTheChannelThroughTheSharedMapping() {
        val s = src("sms/SmsSupport.kt")
        assertTrue("ensureChannels is never called", s.contains("ensureChannels(context)"))
        assertTrue("channelIdFor is never used", s.contains("channelIdFor("))
        assertTrue(
            "the channel mapping is not delegated to NotificationChannels",
            s.contains("NotificationChannels.forMessage")
        )
        assertTrue(
            "the poster never resolves the conversation's channel inputs",
            s.contains("conversationChannelTargetBlocking")
        )
        assertTrue(
            "a conversation does not always post on its own channel",
            s.contains("ensureConversationChannel(context, target, category")
        )
    }

    @Test
    fun staleSoundVariantsAreDeletedPerCategory() {
        assertTrue(
            "stale per-category sound variants are never cleaned up",
            src("sms/SmsSupport.kt").contains("NotificationChannels.categoryVariants(category)")
        )
        assertTrue(
            "legacy single-channel variants are never cleaned up",
            src("sms/SmsSupport.kt").contains("LEGACY_VARIANTS.forEach")
        )
    }

    @Test
    fun settingsStoreCarriesAPerCategorySound() {
        val store = src("data/SettingsStore.kt")
        assertTrue(store.contains("fun notificationSoundFor("))
        assertTrue(store.contains("fun setNotificationSoundFor("))
        assertTrue(store.contains("fun hasNotificationSoundOverride("))
    }

    @Test
    fun schemaAddsThePerConversationSoundAtV29() {
        val repo = src("data/Repository.kt")
        assertTrue(
            "fresh schema lacks the sound column",
            repo.contains("notifications_enabled INTEGER NOT NULL DEFAULT 1,\n                sound TEXT NOT NULL DEFAULT ''")
        )
        assertTrue(
            "no v29 migration for the sound column",
            repo.contains("ALTER TABLE conversation_notifications ADD COLUMN sound TEXT NOT NULL DEFAULT ''")
        )
        assertTrue(repo.contains("fun setConversationNotificationSound("))
    }

    @Test
    fun backupImportCarriesTheConversationSoundButToleratesOldBackups() {
        val repo = src("data/Repository.kt")
        assertTrue(
            "backup import never looks for the sound column",
            repo.contains("backupHasColumn(backup, \"conversation_notifications\", \"sound\")")
        )
        assertTrue(
            "old backups (no sound column) must still import",
            repo.contains("\"conversation_id,notifications_enabled\"")
        )
        assertTrue(
            "sound is not selected on a v29 backup",
            repo.contains("\"conversation_id,notifications_enabled,sound\"")
        )
    }

    @Test
    fun everyConversationOwnsAChannelCreatedAndReconciledInOnePlace() {
        val s = src("sms/SmsSupport.kt")
        assertTrue("no conversation channel builder", s.contains("internal fun ensureConversationChannel("))
        assertTrue("the channel is not titled with the shared chat label", s.contains("channelLabel(target)"))
        assertTrue("the label is not delegated to ContactDetails", s.contains("ContactDetails.listLabel"))
        assertTrue("the category is re-derived outside the shared decision", s.contains("internal fun categoryFor("))
        assertTrue("stale tone variants of a conversation are never deleted", s.contains("NotificationChannels.conversationVariants("))
    }

    @Test
    fun theLaunchSyncAndSettingsReconciliationAreWired() {
        assertTrue(
            "nothing creates conversation channels at launch",
            read("java/com/anindra/messages/MessagesApplication.kt")
                .contains("syncConversationChannels")
        )
        val settings = read("java/com/anindra/messages/ui/AdvancedSubScreens.kt")
        assertTrue("changing the sound does not reconcile conversation channels", settings.contains("reconcileAsync("))
        assertTrue(
            "a permanently deleted conversation keeps its channel",
            read("java/com/anindra/messages/MainActivity.kt")
                .contains("deleteConversationChannels")
        )
        val repo = src("data/Repository.kt")
        assertTrue(repo.contains("fun conversationChannelTargetBlocking("))
        assertTrue(repo.contains("fun recentConversationChannelTargetsBlocking("))
        assertTrue(repo.contains("fun liveConversationIdsBlocking("))
        assertTrue(
            "emptying the trash leaves channels behind",
            read("java/com/anindra/messages/MainActivity.kt").contains("pruneConversationChannels")
        )
    }

    @Test
    fun diagnosticsListsEveryCreatedChannel() {
        val report = src("diagnostics/DiagnosticsReport.kt")
        assertTrue(report.contains("NotificationHelper.createdChannels(context)"))
        assertTrue(report.contains("Channels:"))
    }

    @Test
    fun theViewModelCanSetAPerConversationSound() {
        val activity = read("java/com/anindra/messages/MainActivity.kt")
        assertTrue(activity.contains("fun setConversationNotificationSound("))
        assertTrue(activity.contains("syncNotificationChannels"))
    }

    @Test
    fun contactDetailsExposesTheSoundPicker() {
        val screen = read("java/com/anindra/messages/ui/ContactDetailsScreen.kt")
        assertTrue(screen.contains("R.string.contact_notification_sound"))
        assertTrue(screen.contains("setConversationNotificationSound"))
    }

    @Test
    fun everyLocaleDefinesTheNewKeys() {
        val keys = listOf(
            "notification_channel_direct_title",
            "notification_channel_group_title",
            "notification_channel_unknown_title",
            "notification_channel_convo_desc",
            "contact_notification_sound",
            "contact_notification_sound_default",
            "settings_notif_manage_channels",
            "settings_notif_manage_channels_subtitle"
        )
        val baseText = File(main, "res/values").listFiles { f -> f.name.startsWith("strings") }
            .orEmpty().joinToString("\n") { it.readText() }
        keys.forEach { key ->
            assertTrue("base values missing $key", baseText.contains("name=\"$key\""))
        }
        File(main, "res").listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
            .orEmpty()
            .filter { it.listFiles { f -> f.name.startsWith("strings") }?.isNotEmpty() == true }
            .forEach { dir ->
                val text = dir.listFiles { f -> f.name.startsWith("strings") }
                    .orEmpty().joinToString("\n") { it.readText() }
                keys.forEach { key ->
                    assertTrue("${dir.name} missing $key", text.contains("name=\"$key\""))
                }
            }
    }
}
