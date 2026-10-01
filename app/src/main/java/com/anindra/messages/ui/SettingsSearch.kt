package com.anindra.messages.ui

import com.anindra.messages.R

/**
 * Filtering for a settings screen whose rows are laid out in groups.
 *
 * The settings screens declare their rows as composables rather than as data, so
 * each group states the labels its rows can be found by and keeps the indices
 * that survive the current query. A group with no surviving row is dropped
 * entirely rather than left as an empty card.
 */
object SettingsSearch {

    /** True when [query] matches any of [labels]. An empty query matches all. */
    fun matches(query: String, vararg labels: String?): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return labels.any { !it.isNullOrBlank() && it.contains(q, ignoreCase = true) }
    }

    /**
     * Indices of [entries] (row title to row subtitle) that match [query].
     * An empty query keeps everything, which is what makes search a no-op until
     * the user actually types.
     */
    fun visibleIndices(query: String, entries: List<Pair<String, String?>>): Set<Int> {
        if (query.isBlank()) return entries.indices.toSet()
        return entries.indices
            .filter { matches(query, entries[it].first, entries[it].second) }
            .toSet()
    }

    /**
     * Which card each option on the legacy General settings screen lives in.
     *
     * Search there works like a phone dialler rather than a filter: the results
     * are a plain list of names, and choosing one jumps to the card it belongs
     * to and flashes it. That needs the card each name is drawn in, which the
     * screen itself does not declare -- the rows are inline composables.
     *
     * Listed by group so the index and the screen cannot drift apart silently;
     * [LegacySettingsIndexTest] checks the two still agree.
     */
    object LegacySettingsIndex {

        /** `@StringRes` ids in screen order, grouped by the card they render in. */
        val GROUPS: List<List<Int>> = listOf(
            listOf(
                R.string.settings_notif_title,
                R.string.settings_pin_notification_sound,
                R.string.settings_delivery_reports_title,
                R.string.settings_mark_read_title
            ),
            listOf(
                R.string.settings_theme_title,
                R.string.settings_pin_sim_card,
                R.string.settings_sim_indicator
            ),
            listOf(
                R.string.settings_archiving_title,
                R.string.settings_pinned_title,
                R.string.settings_swipe_actions_title,
                R.string.settings_unread_top_title
            ),
            listOf(
                R.string.settings_scheduled_manage_title
            ),
            listOf(
                R.string.settings_forwarding_title,
                R.string.settings_scheduled_title,
                R.string.settings_delayed_title,
                R.string.settings_delay_secs_title
            ),
            listOf(
                R.string.settings_blocking_title,
                R.string.settings_trash_title,
                R.string.conversations_spam_blocked,
                R.string.settings_backup_title,
                R.string.settings_import_title
            ),
            listOf(
                R.string.settings_advanced_title
            )
        )

        /** Every searchable option, in the order they appear down the screen. */
        val ALL: List<Int> = GROUPS.flatten()

        /** Index into [GROUPS] of the card [rowRes] is drawn in, or -1. */
        fun groupOf(rowRes: Int): Int = GROUPS.indexOfFirst { rowRes in it }
    }
}