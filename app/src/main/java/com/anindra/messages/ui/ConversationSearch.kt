package com.anindra.messages.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.anindra.messages.data.Conversation
import com.anindra.messages.AppViewModel
import com.anindra.messages.data.AddressIdentity
import com.anindra.messages.data.ConversationList
import com.anindra.messages.hideUrls
import kotlinx.coroutines.delay

/**
 * Search state shared by the redesigned list and the legacy one.
 *
 * Both conversation lists used to carry their own inline copy of this, and the
 * copies drifted: the legacy list missed the phone-number match, and neither
 * could see a hit buried in an older message. One holder keeps them equal.
 */

/** Long enough to coalesce a burst of typing, short enough to feel immediate. */
private const val SEARCH_DEBOUNCE_MS = 180L

/**
 * Holds the last resolved message-match ids while a new query is still being
 * answered.
 *
 * Collecting with an empty initial value blanked the results for a frame on
 * every keystroke, and a number query leans on this set entirely — it matches
 * no contact name — so the whole result list vanished and reappeared as the
 * user typed (issue #284).
 *
 * Deliberately *not* filtered by which query produced it: returning the
 * outgoing set keeps the list populated while the new one settles. The brief
 * over-inclusion is invisible; a blank is not. It is a plain class rather than
 * composable state so the rule can be unit tested without a UI harness.
 */
class HeldMessageMatches {
    private var settledQuery: String = ""
    private var settled: Set<Long> = emptySet()

    /** A blank query has no matches at all and clears immediately. */
    fun onQueryStarted(query: String) {
        if (query.isBlank()) {
            settledQuery = ""
            settled = emptySet()
        }
    }

    fun onResultArrived(query: String, ids: Set<Long>) {
        settledQuery = query
        settled = ids
    }

    /** Ids to filter with right now, including while a new query is in flight. */
    fun current(): Set<Long> = settled

    /** True once [current] is known to belong to [query]. */
    fun isSettledFor(query: String): Boolean = settledQuery == query
}

/**
 * Conversation ids with a hit anywhere in their history, for the active
 * [query]. Shared by both conversation lists.
 */
@Composable
fun rememberMessageMatchIds(vm: AppViewModel, query: String): Set<Long> {
    val held = remember { HeldMessageMatches() }
    var ids by remember { mutableStateOf(emptySet<Long>()) }
    // Keyed on the setting too: a toggle has to re-answer the same query, and
    // the visibility rule reads it on the repository side.
    val hideLinks = vm.settings.hideLinks
    LaunchedEffect(query, hideLinks) {
        // A number query has no message hits by definition — ConversationList.filter
        // matches it on address alone — so don't pay for the LIKE scan over every
        // message body. Clearing the held set too stops a previous text query's ids
        // surviving, which would over-include if a letter is typed mid-number.
        if (AddressIdentity.isNumberQuery(query)) {
            held.onQueryStarted("")
            ids = emptySet()
            return@LaunchedEffect
        }
        held.onQueryStarted(query)
        if (query.isBlank()) {
            ids = held.current()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        vm.conversationIdsMatchingMessage(query, hideLinks).collect {
            held.onResultArrived(query, it)
            ids = held.current()
        }
    }
    return ids
}

/**
 * The conversations the list should show: inbox or archive, filtered by
 * [query] and sorted. Shared so the two lists cannot diverge again.
 */
@Composable
fun rememberSearchResults(
    conversations: List<Conversation>,
    showArchived: Boolean,
    query: String,
    unreadAtTop: Boolean,
    hideLinks: Boolean,
    messageMatchIds: Set<Long>
): List<Conversation> = remember(
    conversations, showArchived, query, unreadAtTop, hideLinks, messageMatchIds
) {
    // Ranked after the sort, not before: unread-at-top re-sorts the whole list,
    // so a contact put first beforehand would be moved back down by it.
    ConversationList.nameMatchesFirst(
        ConversationList.sort(
            ConversationList.filter(
                conversations, showArchived, query, messageMatchIds
            ) { if (hideLinks) hideUrls(it.snippet) else it.snippet },
            unreadAtTop, showArchived
        ),
        query
    )
}