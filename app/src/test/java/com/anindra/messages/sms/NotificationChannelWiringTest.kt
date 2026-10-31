package com.anindra.messages.sms

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wiring for the per-category channels (#339) and the opt-in per-chat channels
 * (#339 follow-up): the schema column, the settings store, the poster and
 * Diagnostics all have to agree, so each side is pinned against the source that
 * owns it.
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
        // A chat posts on its own channel only while that channel exists; the
        // check is what keeps a message alive after the user removes it.
        assertTrue(
            "posting does not check that the chat's channel exists",
            s.contains("conversationChannelExists(context, convoId)")
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
    fun thePerConversationSoundColumnStaysForRestores() {
        // Nothing reads the column any more — a chat's tone belongs to its system
        // channel — but backups written before the change still carry it, so the
        // schema and the restore path keep it rather than paying for a migration.
        val repo = src("data/Repository.kt")
        assertTrue(
            "fresh schema lost the sound column",
            repo.contains("notifications_enabled INTEGER NOT NULL DEFAULT 1,\n                sound TEXT NOT NULL DEFAULT ''")
        )
        assertTrue(
            "no v29 migration for the sound column",
            repo.contains("ALTER TABLE conversation_notifications ADD COLUMN sound TEXT NOT NULL DEFAULT ''")
        )
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
    fun aConversationChannelIsBuiltFromTheSharedLabelAndStableId() {
        val s = src("sms/SmsSupport.kt")
        assertTrue("no conversation channel builder", s.contains("internal fun ensureConversationChannel("))
        assertTrue("the channel is not titled with the shared chat label", s.contains("channelLabel(target)"))
        assertTrue("the label is not delegated to ContactDetails", s.contains("ContactDetails.listLabel"))
        assertTrue("the category is re-derived outside the shared decision", s.contains("internal fun categoryFor("))
        assertTrue(
            "the channel id is not the stable one",
            s.contains("NotificationChannels.forConversation(target.conversationId)")
        )
        assertTrue(
            "old per-tone channels of a conversation are never deleted",
            s.contains("NotificationChannels.legacyConversationVariants(")
        )
        // The sound is seeded on creation only. An upsert that reset it would
        // overwrite whatever the user chose in Android's own settings.
        val body = s.substringAfter("internal fun ensureConversationChannel(")
            .substringBefore("\n    /** The row Android shows")
        assertTrue(
            "the channel's sound is re-applied on every refresh",
            body.contains("if (existing == null) {\n                    setSound(")
        )
    }

    @Test
    fun noChannelIsCreatedForAChatTheUserHasNotAskedAbout() {
        // The bulk sync that gave every recent chat a channel is gone: a fresh
        // install must not dump the address book into system notification
        // settings. Only chats that already own a channel are touched.
        val s = src("sms/SmsSupport.kt")
        assertTrue(
            "the launch sync creates channels again",
            !s.contains("recentConversationChannelTargetsBlocking")
        )
        assertTrue(
            "the launch sync does not walk the channels that exist",
            s.contains("?.notificationChannels")
        )
        val app = read("java/com/anindra/messages/MessagesApplication.kt")
        assertTrue(
            "Application.onCreate still creates channels in bulk",
            !app.contains("syncConversationChannels")
        )
        assertTrue(
            "Application.onCreate never reconciles the channels that exist",
            app.contains("syncNotificationChannels")
        )
    }

    @Test
    fun bulkImportsAndTheFirstRunReconcileInsteadOfCreating() {
        val activity = read("java/com/anindra/messages/MainActivity.kt")
        assertTrue(
            "a bulk import still creates channels",
            !activity.contains("syncConversationChannels")
        )
        val requery = activity.indexOf("repo.requeryFromSystem()")
        assertTrue("the first-run import is gone", requery >= 0)
        val afterRequery = activity.indexOf("reconcileAsync(this)", requery)
        assertTrue("the first-run import leaves stale channels behind", afterRequery > requery)
        // The restore and sms-ie paths still clean up after themselves.
        assertTrue(
            "a restored/imported database never reconciles its channels",
            Regex("syncChannelsAfterBulkImport\\(\\)").findAll(activity).count() >= 3
        )
    }

    @Test
    fun theLaunchSyncAndSettingsReconciliationAreWired() {
        assertTrue(
            "changing a category sound does not reconcile existing channels",
            read("java/com/anindra/messages/ui/AdvancedSubScreens.kt").contains("reconcileAsync(")
        )
        assertTrue(
            "a permanently deleted conversation keeps its channel",
            read("java/com/anindra/messages/MainActivity.kt")
                .contains("deleteConversationChannels")
        )
        val repo = src("data/Repository.kt")
        assertTrue(repo.contains("fun conversationChannelTargetBlocking("))
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
    fun theViewModelTogglesAndOpensTheChatsChannel() {
        val activity = read("java/com/anindra/messages/MainActivity.kt")
        assertTrue(activity.contains("fun conversationCustomChannel("))
        assertTrue(activity.contains("fun setConversationCustomChannel("))
        assertTrue(
            "there is no way to reach the chat's page in system settings",
            activity.contains("Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS")
        )
        assertTrue(
            "the system page is opened without the chat's channel id",
            activity.contains("Settings.EXTRA_CHANNEL_ID")
        )
        assertTrue(
            "the per-chat sound is still stored in the app",
            !activity.contains("fun setConversationNotificationSound(")
        )
    }

    @Test
    fun contactDetailsHandsTheSettingToTheSystem() {
        val screen = read("java/com/anindra/messages/ui/ContactDetailsScreen.kt")
        assertTrue(screen.contains("R.string.contact_custom_notifications"))
        assertTrue(screen.contains("R.string.contact_customize_notifications"))
        assertTrue(screen.contains("setConversationCustomChannel"))
        assertTrue(screen.contains("openConversationChannelSettings"))
        // No second copy of the tone: the app cannot change a channel's sound
        // once it exists, so a picker here could only disagree with Android.
        assertTrue(
            "the in-app per-chat sound picker is back",
            !screen.contains("contactSoundOptions(context)")
        )
        assertTrue(
            "the in-app per-chat sound picker is back",
            !screen.contains("contact_notification_sound\"")
        )
    }

    @Test
    fun everyLocaleDefinesTheNewKeys() {
        val keys = listOf(
            "notification_channel_direct_title",
            "notification_channel_group_title",
            "notification_channel_unknown_title",
            "notification_channel_convo_desc",
            "contact_custom_notifications",
            "contact_customize_notifications",
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
