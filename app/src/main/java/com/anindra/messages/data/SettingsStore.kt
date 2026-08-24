package com.anindra.messages.data

import android.content.Context
import android.content.SharedPreferences

/** Google-Messages-like customization store. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("messages_settings", Context.MODE_PRIVATE)

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val KEY_THEME = "theme_mode"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_SOUNDS = "message_sounds_enabled"
        const val KEY_DELIVERY_REPORTS = "delivery_reports_enabled"
        const val KEY_SIM_SUBSCRIPTION_ID = "sim_subscription_id"
        const val KEY_PINNED_ENABLED = "pinned_enabled"
        const val KEY_ARCHIVING_ENABLED = "archiving_enabled"
        const val KEY_DRAFTS_ENABLED = "drafts_enabled"
        const val KEY_SWIPE_ACTIONS_ENABLED = "swipe_actions_enabled"
        const val KEY_BLOCKING_ENABLED = "blocking_enabled"
        const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        const val KEY_UNREAD_AT_TOP_ENABLED = "unread_at_top_enabled"
        const val KEY_SCHEDULED_MESSAGES_ENABLED = "scheduled_messages_enabled"
        const val KEY_DELAYED_SENDING_ENABLED = "delayed_sending_enabled"
        const val KEY_DELAY_SECONDS = "delay_seconds"
        const val KEY_HIGHLIGHT_LINKS = "highlight_links"
        const val KEY_PRIVACY_MODE = "privacy_mode"
        const val KEY_APP_LOCK = "app_lock_enabled"
        const val KEY_FIRST_IMPORT_DONE = "first_import_done"
        const val KEY_INCOMING_SOUND = "incoming_sound_uri"
        const val KEY_OUTGOING_SOUND = "outgoing_sound_uri"
        const val SOUND_DEFAULT = "default"
        const val SOUND_SILENT = "silent"
        const val DEFAULTS_NOTIFICATIONS = true
        const val DEFAULTS_SOUNDS = true
        const val DEFAULTS_DELIVERY = false
        const val DEFAULTS_SIM_SUBSCRIPTION_ID = -1
    }

    var themeMode: String
        get() = prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, DEFAULTS_NOTIFICATIONS)
        set(v) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, v).apply()

    var soundsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUNDS, DEFAULTS_SOUNDS)
        set(v) = prefs.edit().putBoolean(KEY_SOUNDS, v).apply()

    var deliveryReportsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DELIVERY_REPORTS, DEFAULTS_DELIVERY)
        set(v) = prefs.edit().putBoolean(KEY_DELIVERY_REPORTS, v).apply()

    var simSubscriptionId: Int
        get() = prefs.getInt(KEY_SIM_SUBSCRIPTION_ID, DEFAULTS_SIM_SUBSCRIPTION_ID)
        set(v) = prefs.edit().putInt(KEY_SIM_SUBSCRIPTION_ID, v).apply()

    /** True once the post-install message import has completed at least once;
     *  later launches render the local DB instantly without a loading state. */
    var firstImportDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_IMPORT_DONE, false)
        set(v) = prefs.edit().putBoolean(KEY_FIRST_IMPORT_DONE, v).apply()

    var pinnedEnabled: Boolean
        get() = prefs.getBoolean(KEY_PINNED_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_PINNED_ENABLED, v).apply()

    var archivingEnabled: Boolean
        get() = prefs.getBoolean(KEY_ARCHIVING_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_ARCHIVING_ENABLED, v).apply()

    var draftsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DRAFTS_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_DRAFTS_ENABLED, v).apply()

    var swipeActionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_ACTIONS_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_SWIPE_ACTIONS_ENABLED, v).apply()

    var blockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCKING_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_BLOCKING_ENABLED, v).apply()

    var forwardingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_FORWARDING_ENABLED, v).apply()

    var unreadAtTopEnabled: Boolean
        get() = prefs.getBoolean(KEY_UNREAD_AT_TOP_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_UNREAD_AT_TOP_ENABLED, v).apply()

    var scheduledMessagesEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULED_MESSAGES_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_SCHEDULED_MESSAGES_ENABLED, v).apply()

    var delayedSendingEnabled: Boolean
        get() = prefs.getBoolean(KEY_DELAYED_SENDING_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_DELAYED_SENDING_ENABLED, v).apply()

    var highlightLinks: Boolean
        get() = prefs.getBoolean(KEY_HIGHLIGHT_LINKS, true)
        set(v) = prefs.edit().putBoolean(KEY_HIGHLIGHT_LINKS, v).apply()

    var privacyModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_MODE, false)
        set(v) = prefs.edit().putBoolean(KEY_PRIVACY_MODE, v).apply()

    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(v) = prefs.edit().putBoolean(KEY_APP_LOCK, v).apply()

    var delaySeconds: Int
        get() = prefs.getInt(KEY_DELAY_SECONDS, 3)
        set(v) = prefs.edit().putInt(KEY_DELAY_SECONDS, v).apply()

    var incomingSound: String
        get() = prefs.getString(KEY_INCOMING_SOUND, SOUND_DEFAULT) ?: SOUND_DEFAULT
        set(v) = prefs.edit().putString(KEY_INCOMING_SOUND, v).apply()

    var outgoingSound: String
        get() = prefs.getString(KEY_OUTGOING_SOUND, SOUND_DEFAULT) ?: SOUND_DEFAULT
        set(v) = prefs.edit().putString(KEY_OUTGOING_SOUND, v).apply()

    var incomingSoundLabel: String
        get() = prefs.getString("${KEY_INCOMING_SOUND}_label", "") ?: ""
        set(v) = prefs.edit().putString("${KEY_INCOMING_SOUND}_label", v).apply()

    var outgoingSoundLabel: String
        get() = prefs.getString("${KEY_OUTGOING_SOUND}_label", "") ?: ""
        set(v) = prefs.edit().putString("${KEY_OUTGOING_SOUND}_label", v).apply()
}
