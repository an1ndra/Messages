package com.anindra.messages.sms

import com.anindra.messages.data.SettingsStore

/**
 * Which notification channel an incoming message belongs to.
 *
 * Distinct channels are the only way an app can offer per-category alert
 * control: a channel's sound/vibration/importance become user-owned knobs in
 * system settings as soon as it exists, and the app cannot change them
 * afterwards.
 */
enum class NotificationCategory {
    DIRECT,
    GROUP,
    UNKNOWN;

    companion object {
        /**
         * Group wins over [UNKNOWN]: a group of strangers is still a group.
         * [UNKNOWN] covers alphanumeric/short-code senders (not [replyable]) and
         * numbers that are not in Contacts ([knownContact] false).
         */
        fun resolve(isGroup: Boolean, knownContact: Boolean, replyable: Boolean): NotificationCategory =
            when {
                isGroup -> GROUP
                !knownContact || !replyable -> UNKNOWN
                else -> DIRECT
            }

        /** Stable per-category preference key, independent of the enum name. */
        fun keyOf(category: NotificationCategory): String = when (category) {
            NotificationCategory.DIRECT -> "direct"
            NotificationCategory.GROUP -> "group"
            NotificationCategory.UNKNOWN -> "unknown"
        }
    }
}

/**
 * Channel ids, in one place so the poster, the settings screen and Diagnostics
 * cannot drift apart.
 *
 * A channel's sound cannot be changed once the channel exists, so — exactly as
 * the pre-category app did for its one channel — each category carries one
 * channel id per sound selection. Changing a category's sound switches to a
 * fresh id and the app deletes the stale variant.
 */
object NotificationChannels {
    const val PREFIX = "messages"

    /** Per-sound suffixes; "silent" is the receive-sound-off case. */
    const val SUFFIX_DEFAULT = "default"
    const val SUFFIX_APP = "app"
    const val SUFFIX_DRAGON = "dragon"
    const val SUFFIX_UF09 = "uf09"
    const val SUFFIX_UF062 = "uf062"
    const val SUFFIX_SILENT = "silent"

    private val SOUND_SUFFIXES = listOf(
        SUFFIX_DEFAULT, SUFFIX_APP, SUFFIX_DRAGON, SUFFIX_UF09, SUFFIX_UF062, SUFFIX_SILENT
    )

    /** The suffix for a sound selection, or "silent" when receive sound is off. */
    fun soundSuffix(selection: String, soundOn: Boolean): String = when {
        !soundOn -> SUFFIX_SILENT
        selection == SettingsStore.NOTIFY_SOUND_APP -> SUFFIX_APP
        selection == SettingsStore.NOTIFY_SOUND_DRAGON -> SUFFIX_DRAGON
        selection == SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_09 -> SUFFIX_UF09
        selection == SettingsStore.NOTIFY_SOUND_UNIVERSFIELD_062 -> SUFFIX_UF062
        else -> SUFFIX_DEFAULT
    }

    private fun categoryPrefix(category: NotificationCategory): String = when (category) {
        NotificationCategory.DIRECT -> "${PREFIX}_direct"
        NotificationCategory.GROUP -> "${PREFIX}_group"
        NotificationCategory.UNKNOWN -> "${PREFIX}_unknown"
    }

    /** The live channel for a category, given its sound selection. */
    fun forCategory(category: NotificationCategory, selection: String, soundOn: Boolean): String =
        "${categoryPrefix(category)}_${soundSuffix(selection, soundOn)}"

    /** Every id a category could own, so the stale ones can be cleaned up. */
    fun categoryVariants(category: NotificationCategory): List<String> =
        categoryPrefix(category).let { p -> SOUND_SUFFIXES.map { "${p}_$it" } }

    /**
     * The channel a conversation posts to: every conversation owns one (#339),
     * so the category channel is only the fallback for a sender with no
     * conversation row yet. The inherited tone is the override when there is
     * one, else the category's.
     */
    fun forMessage(
        category: NotificationCategory,
        selection: String,
        soundOn: Boolean,
        conversationId: Long?,
        overrideSound: String
    ): String =
        if (conversationId != null) {
            forConversation(conversationId, overrideSound.ifEmpty { selection }, soundOn)
        } else {
            forCategory(category, selection, soundOn)
        }

    fun conversationPrefix(conversationId: Long): String = "${PREFIX}_convo_$conversationId"

    /**
     * A conversation's channel id. The sound is part of the id because a
     * channel's tone cannot be changed once created, so changing it has to move
     * to a fresh id and delete the old one.
     */
    fun forConversation(conversationId: Long, sound: String, soundOn: Boolean): String =
        "${conversationPrefix(conversationId)}_${soundSuffix(sound, soundOn)}"

    /** Every id one conversation could own, so the stale ones can be deleted. */
    fun conversationVariants(conversationId: Long): List<String> =
        conversationPrefix(conversationId).let { p -> SOUND_SUFFIXES.map { "${p}_$it" } }

    /**
     * The conversation a channel id belongs to, or null for a category or
     * legacy channel. Used to reconcile and prune channels against the database,
     * so the id is the only thing that has to round-trip.
     */
    fun conversationIdOf(channelId: String): Long? =
        Regex("^${PREFIX}_convo_(\\d+)_[a-z0-9]+$")
            .find(channelId)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

    /** The system-settings display name for a category channel. */
    fun titleRes(category: NotificationCategory): Int = when (category) {
        NotificationCategory.DIRECT -> com.anindra.messages.R.string.notification_channel_direct_title
        NotificationCategory.GROUP -> com.anindra.messages.R.string.notification_channel_group_title
        NotificationCategory.UNKNOWN -> com.anindra.messages.R.string.notification_channel_unknown_title
    }

    /**
     * The single-channel ids used before categories existed. They are deleted
     * once the category channels are in place; none of them is reused (every
     * category id carries a category segment), so a stale one can never shadow a
     * new channel.
     */
    val LEGACY_VARIANTS = listOf(
        "${PREFIX}_default",
        "${PREFIX}_app",
        "${PREFIX}_dragon",
        "${PREFIX}_uf09",
        "${PREFIX}_uf062",
        "${PREFIX}_silent",
        PREFIX
    )
}
