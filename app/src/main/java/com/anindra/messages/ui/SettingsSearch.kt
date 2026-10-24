package com.anindra.messages.ui

import com.anindra.messages.R

/**
 * Where an option on the legacy settings tree lives, so a search result can
 * navigate to it and flash it.
 *
 * [card] is unique across the whole tree rather than per screen, so a single
 * offset map serves every settings screen: General settings owns 0-6, Advanced
 * 7-14, Accessibility 15-17.
 */
data class SettingsOption(
    val rowRes: Int,
    val card: Int,
    val route: String
)

object SettingsSearch {

    /** Which settings screen an option is found on. */
    object Route {
        const val SETTINGS = "settings"
        const val ADVANCED = "advanced"
        const val ACCESSIBILITY = "accessibility"
    }

    /** True when [query] matches any of [labels]. An empty query matches all. */
    fun matches(query: String, vararg labels: String?): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return labels.any { !it.isNullOrBlank() && it.contains(q, ignoreCase = true) }
    }

    /**
     * Every searchable option on the legacy settings tree, in the order the
     * options appear down each screen.
     */
    val LEGACY_OPTIONS: List<SettingsOption> = buildList {
        fun card(
            route: String,
            index: Int,
            vararg rows: Int
        ) = rows.forEach { add(SettingsOption(it, index, route)) }

        card(Route.SETTINGS, 0,
            R.string.settings_notif_title,
            R.string.settings_pin_notification_sound,
            R.string.settings_delivery_reports_title,
            R.string.settings_mark_read_title)
        card(Route.SETTINGS, 1,
            R.string.settings_theme_title,
            R.string.settings_pin_sim_card,
            R.string.settings_sim_indicator)
        card(Route.SETTINGS, 2,
            R.string.settings_archiving_title,
            R.string.settings_pinned_title,
            R.string.settings_swipe_actions_title,
            R.string.settings_unread_top_title)
        card(Route.SETTINGS, 3,
            R.string.settings_scheduled_manage_title)
        card(Route.SETTINGS, 4,
            R.string.settings_forwarding_title,
            R.string.settings_scheduled_title,
            R.string.settings_delayed_title,
            R.string.settings_delay_secs_title)
        card(Route.SETTINGS, 5,
            R.string.settings_blocking_title,
            R.string.settings_trash_title,
            R.string.conversations_spam_blocked,
            R.string.settings_backup_title,
            R.string.settings_import_title)
        card(Route.SETTINGS, 6,
            R.string.settings_advanced_title)

        card(Route.ADVANCED, 7,
            R.string.settings_drafts_title,
            R.string.settings_advanced_reverse_swipe,
            R.string.settings_advanced_permanent_delete)
        card(Route.ADVANCED, 8,
            R.string.settings_advanced_hide_links,
            R.string.settings_advanced_highlight_links,
            R.string.settings_advanced_link_warning)
        card(Route.ADVANCED, 9,
            R.string.settings_privacy_title,
            R.string.settings_applock_title,
            R.string.keywords_title)
        card(Route.ADVANCED, 10,
            R.string.settings_send_sound_title,
            R.string.settings_receive_sound_title,
            R.string.settings_notif_action_reply_title,
            R.string.settings_notif_action_mark_read_title,
            R.string.settings_notif_action_delete_title)
        card(Route.ADVANCED, 11,
            R.string.settings_font_title,
            R.string.settings_advanced_emoji_button)
        card(Route.ADVANCED, 12,
            R.string.settings_accessibility_title,
            R.string.accessibility_options_title)
        card(Route.ADVANCED, 13,
            R.string.settings_retention_title,
            R.string.settings_retention_trash,
            R.string.settings_retention_keep_trash,
            R.string.settings_retention_keyword,
            R.string.settings_retention_blocked,
            R.string.settings_retention_keep_spam)
        card(Route.ADVANCED, 14,
            R.string.mms_check_title,
            R.string.settings_new_ui_title,
            R.string.transfer_log_title,
            R.string.diagnostics_title)

        card(Route.ACCESSIBILITY, 15,
            R.string.a11y_font_size_title,
            R.string.a11y_bold_title)
        card(Route.ACCESSIBILITY, 16,
            R.string.a11y_high_contrast_title,
            R.string.a11y_large_touch_title)
        card(Route.ACCESSIBILITY, 17,
            R.string.a11y_reduce_motion_title)
    }

    /** Row resources of every searchable option, in screen order. */
    val LEGACY_ROWS: List<Int> = LEGACY_OPTIONS.map { it.rowRes }

    /** Card index [rowRes] is drawn in, or -1 when it is not searchable. */
    fun cardOf(rowRes: Int): Int =
        LEGACY_OPTIONS.firstOrNull { it.rowRes == rowRes }?.card ?: -1

    /** Screen [rowRes] lives on. Defaults to the settings list. */
    fun routeOf(rowRes: Int): String =
        LEGACY_OPTIONS.firstOrNull { it.rowRes == rowRes }?.route ?: Route.SETTINGS
}