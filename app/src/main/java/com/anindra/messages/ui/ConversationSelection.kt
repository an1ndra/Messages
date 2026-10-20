package com.anindra.messages.ui

/**
 * Selection rules for the conversation list's multi-select mode (#340). Kept out
 * of the composable so the set arithmetic is unit tested.
 */
object ConversationSelection {

    fun isActive(selected: Set<Long>): Boolean = selected.isNotEmpty()

    fun toggle(selected: Set<Long>, id: Long): Set<Long> =
        if (id in selected) selected - id else selected + id

    /** "Select all" takes every row the user can see right now, not the rows that
     *  happen to be in the database — a search or the archive view narrows it. */
    fun selectAll(visibleIds: List<Long>): Set<Long> = visibleIds.toSet()

    /** Drops ids that are no longer on screen (deleted, filtered out by a search)
     *  so the bar cannot count rows the user cannot see. */
    fun prune(selected: Set<Long>, visibleIds: List<Long>): Set<Long> =
        selected intersect visibleIds.toSet()

    fun trashTargets(selected: Set<Long>, visibleIds: List<Long>): List<Long> =
        visibleIds.filter { it in selected }
}
