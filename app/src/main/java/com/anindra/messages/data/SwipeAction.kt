package com.anindra.messages.data

/**
 * What a swipe on a conversation row does. Each direction is configured
 * independently, so both sides can be off, or the same action, or different ones.
 */
enum class SwipeAction(val storageValue: Int) {
    OFF(0),
    ARCHIVE(1),
    DELETE(2),
    MARK_READ_UNREAD(3),
    PIN(4),
    BLOCK(5);

    companion object {
        /** Unknown stored values fall back to [ARCHIVE] rather than throwing. */
        fun fromStorage(v: Int): SwipeAction =
            entries.firstOrNull { it.storageValue == v } ?: ARCHIVE

        /**
         * The pair a pre-per-direction install had, derived from the old
         * enabled/reverse booleans. `enabled=false` disabled both directions.
         */
        fun legacyPair(enabled: Boolean, reverse: Boolean): Pair<SwipeAction, SwipeAction> {
            if (!enabled) return OFF to OFF
            return if (reverse) DELETE to ARCHIVE else ARCHIVE to DELETE
        }
    }
}
