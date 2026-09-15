package com.anindra.messages.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Google-Messages-like customization store. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("messages_settings", Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

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
        const val KEY_HIDE_LINKS = "hide_links"
        const val KEY_PRIVACY_MODE = "privacy_mode"
        const val KEY_APP_LOCK = "app_lock_enabled"
        const val KEY_FIRST_IMPORT_DONE = "first_import_done"
        const val KEY_PARTICIPANTS_MIGRATED = "participants_migrated"
        const val KEY_PHONE_REGION = "phone_region"
        const val KEY_SEND_SOUND = "send_sound_enabled"
        const val KEY_RECEIVE_SOUND = "receive_sound_enabled"
        const val KEY_NOTIFICATION_SOUND = "notification_sound"
        const val NOTIFY_SOUND_DEFAULT = "default"
        const val NOTIFY_SOUND_APP = "app_sound"
        const val NOTIFY_SOUND_DRAGON = "dragon_studio"
        const val NOTIFY_SOUND_UNIVERSFIELD_09 = "universfield_09"
        const val NOTIFY_SOUND_UNIVERSFIELD_062 = "universfield_062"
        const val KEY_SHOW_SIM_INDICATOR = "show_sim_indicator"
        const val KEY_PERMANENT_DELETE = "permanent_delete_enabled"
        const val KEY_PERMANENT_DELETE_WARN = "permanent_delete_warn"
        const val KEY_REVERSE_SWIPE = "reverse_swipe_enabled"
        const val KEY_LINK_WARNING = "link_open_warning_enabled"
        const val KEY_FONT_FAMILY = "font_family"
        const val FONT_SYSTEM = "system"
        const val FONT_DM_SANS = "dm_sans"
        const val FONT_INTER = "inter"
        const val FONT_FIGTREE = "figtree"
        const val FONT_POPPINS = "poppins"
        const val KEY_BLOCKED_KEYWORDS = "blocked_keywords"
        const val DEFAULTS_NOTIFICATIONS = true
        const val DEFAULTS_SOUNDS = true
        const val DEFAULTS_DELIVERY = false
        const val DEFAULTS_SIM_SUBSCRIPTION_ID = -1
    }

    var themeMode: String
        get() = prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(v) { prefs.edit().putString(KEY_THEME, v).apply(); _revision.value++ }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, DEFAULTS_NOTIFICATIONS)
        set(v) { prefs.edit().putBoolean(KEY_NOTIFICATIONS, v).apply(); _revision.value++ }

    var soundsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUNDS, DEFAULTS_SOUNDS)
        set(v) { prefs.edit().putBoolean(KEY_SOUNDS, v).apply(); _revision.value++ }

    var deliveryReportsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DELIVERY_REPORTS, DEFAULTS_DELIVERY)
        set(v) { prefs.edit().putBoolean(KEY_DELIVERY_REPORTS, v).apply(); _revision.value++ }

    var simSubscriptionId: Int
        get() = prefs.getInt(KEY_SIM_SUBSCRIPTION_ID, DEFAULTS_SIM_SUBSCRIPTION_ID)
        set(v) { prefs.edit().putInt(KEY_SIM_SUBSCRIPTION_ID, v).apply(); _revision.value++ }

    var firstImportDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_IMPORT_DONE, false)
        set(v) { prefs.edit().putBoolean(KEY_FIRST_IMPORT_DONE, v).apply(); _revision.value++ }

    var participantsMigrated: Boolean
        get() = prefs.getBoolean(KEY_PARTICIPANTS_MIGRATED, false)
        set(v) { prefs.edit().putBoolean(KEY_PARTICIPANTS_MIGRATED, v).apply() }

    var phoneRegion: String
        get() = prefs.getString(KEY_PHONE_REGION, "") ?: ""
        set(v) { prefs.edit().putString(KEY_PHONE_REGION, v).apply() }

    var pinnedEnabled: Boolean
        get() = prefs.getBoolean(KEY_PINNED_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_PINNED_ENABLED, v).apply(); _revision.value++ }

    var archivingEnabled: Boolean
        get() = prefs.getBoolean(KEY_ARCHIVING_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_ARCHIVING_ENABLED, v).apply(); _revision.value++ }

    var draftsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DRAFTS_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_DRAFTS_ENABLED, v).apply(); _revision.value++ }

    var swipeActionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_ACTIONS_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_SWIPE_ACTIONS_ENABLED, v).apply(); _revision.value++ }

    var blockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCKING_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_BLOCKING_ENABLED, v).apply(); _revision.value++ }

    var forwardingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_FORWARDING_ENABLED, v).apply(); _revision.value++ }

    var unreadAtTopEnabled: Boolean
        get() = prefs.getBoolean(KEY_UNREAD_AT_TOP_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_UNREAD_AT_TOP_ENABLED, v).apply(); _revision.value++ }

    var scheduledMessagesEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULED_MESSAGES_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_SCHEDULED_MESSAGES_ENABLED, v).apply(); _revision.value++ }

    var delayedSendingEnabled: Boolean
        get() = prefs.getBoolean(KEY_DELAYED_SENDING_ENABLED, false)
        set(v) { prefs.edit().putBoolean(KEY_DELAYED_SENDING_ENABLED, v).apply(); _revision.value++ }

    var highlightLinks: Boolean
        get() = prefs.getBoolean(KEY_HIGHLIGHT_LINKS, true)
        set(v) {
            val editor = prefs.edit().putBoolean(KEY_HIGHLIGHT_LINKS, v)
            if (!v) editor.putBoolean(KEY_LINK_WARNING, false)
            editor.apply()
            _revision.value++
        }

    var hideLinks: Boolean
        get() = prefs.getBoolean(KEY_HIDE_LINKS, false)
        set(v) {
            val editor = prefs.edit().putBoolean(KEY_HIDE_LINKS, v)
            if (v) {
                editor.putBoolean(KEY_HIGHLIGHT_LINKS, false)
                editor.putBoolean(KEY_LINK_WARNING, false)
            }
            editor.apply()
            _revision.value++
        }

    var privacyModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_MODE, false)
        set(v) { prefs.edit().putBoolean(KEY_PRIVACY_MODE, v).apply(); _revision.value++ }

    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(v) { prefs.edit().putBoolean(KEY_APP_LOCK, v).apply(); _revision.value++ }

    var delaySeconds: Int
        get() = prefs.getInt(KEY_DELAY_SECONDS, 3)
        set(v) { prefs.edit().putInt(KEY_DELAY_SECONDS, v).apply(); _revision.value++ }

    var sendSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SEND_SOUND, DEFAULTS_SOUNDS)
        set(v) { prefs.edit().putBoolean(KEY_SEND_SOUND, v).apply(); _revision.value++ }

    var receiveSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECEIVE_SOUND, DEFAULTS_SOUNDS)
        set(v) { prefs.edit().putBoolean(KEY_RECEIVE_SOUND, v).apply(); _revision.value++ }

    var notificationSound: String
        get() = prefs.getString(KEY_NOTIFICATION_SOUND, NOTIFY_SOUND_DEFAULT) ?: NOTIFY_SOUND_DEFAULT
        set(v) { prefs.edit().putString(KEY_NOTIFICATION_SOUND, v).apply(); _revision.value++ }

    var showSimIndicator: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SIM_INDICATOR, true)
        set(v) { prefs.edit().putBoolean(KEY_SHOW_SIM_INDICATOR, v).apply(); _revision.value++ }

    var permanentDeleteEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERMANENT_DELETE, false)
        set(v) { prefs.edit().putBoolean(KEY_PERMANENT_DELETE, v).apply(); _revision.value++ }

    var permanentDeleteWarn: Boolean
        get() = prefs.getBoolean(KEY_PERMANENT_DELETE_WARN, true)
        set(v) { prefs.edit().putBoolean(KEY_PERMANENT_DELETE_WARN, v).apply(); _revision.value++ }

    var reverseSwipeEnabled: Boolean
        get() = prefs.getBoolean(KEY_REVERSE_SWIPE, false)
        set(v) { prefs.edit().putBoolean(KEY_REVERSE_SWIPE, v).apply(); _revision.value++ }

    var linkOpenWarningEnabled: Boolean
        get() = prefs.getBoolean(KEY_LINK_WARNING, true)
        set(v) {
            val enabled = v && highlightLinks && !hideLinks
            prefs.edit().putBoolean(KEY_LINK_WARNING, enabled).apply()
            _revision.value++
        }

    var fontFamily: String
        get() = prefs.getString(KEY_FONT_FAMILY, FONT_SYSTEM) ?: FONT_SYSTEM
        set(v) { prefs.edit().putString(KEY_FONT_FAMILY, v).apply(); _revision.value++ }

    var blockedKeywords: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_KEYWORDS, emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_BLOCKED_KEYWORDS, v).apply(); _revision.value++ }

    /** True when [body] contains any blocked keyword (case-insensitive). */
    fun isKeywordBlocked(body: String): Boolean = KeywordFilter.isBlocked(body, blockedKeywords)
}
