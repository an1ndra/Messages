package com.anindra.messages.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anindra.messages.data.Conversation
import com.anindra.messages.AppViewModel
import com.anindra.messages.data.AddressIdentity
import com.anindra.messages.data.CalendarSystem
import com.anindra.messages.data.ConversationList
import com.anindra.messages.data.MessageSearchFilters
import com.anindra.messages.data.SearchFilters
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
 * A query with its operators understood: the text the search should match,
 * the message-level filters to narrow the scan with, and who `from:` narrowed
 * it to.
 *
 * The dates are converted once per (query, calendar) here, against the
 * resolved calendar — the same one the schedule grid and the date pickers
 * read — so a typed date means the day the user sees everywhere else.
 */
class ResolvedSearchQuery internal constructor(
    val parsed: SearchFilters.Parsed,
    val text: String,
    val messageFilters: MessageSearchFilters?,
    val from: String?
)

@Composable
fun rememberSearchQueryFilters(
    rawQuery: String,
    calendar: CalendarSystem
): ResolvedSearchQuery {
    val parsed = remember(rawQuery) { SearchFilters.parse(rawQuery) }
    val filters = parsed.filters
    return remember(parsed, calendar) {
        val months = IcuCalendarMonths(calendar)
        val before = filters.before?.let {
            months.utcMillisOf(it.year, it.month1 - 1, it.day) + DAY_MILLIS - 1
        }
        val after = filters.after?.let {
            months.utcMillisOf(it.year, it.month1 - 1, it.day)
        }
        ResolvedSearchQuery(
            parsed = parsed,
            text = filters.plainText,
            messageFilters = if (filters.hasMessageFilters) {
                MessageSearchFilters(filters.hasAttachment, before, after)
            } else null,
            from = filters.from
        )
    }
}

/** One day, the bound `before:` closes at. */
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * The chips under the search field: one per operator the query actually
 * carried, labelled with the understood filter, tapping one removes it and
 * keeps everything else. A query with no operators shows nothing, so today's
 * searches look exactly as they did.
 */
@Composable
fun SearchFilterChips(
    resolved: ResolvedSearchQuery,
    onRemove: (SearchFilters.FilterKind) -> Unit
) {
    val f = resolved.parsed.filters
    if (!f.hasAttachment && f.from == null && f.before == null && f.after == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        f.from?.let { value ->
            FilterChip(
                selected = true,
                onClick = { onRemove(SearchFilters.FilterKind.FROM) },
                label = {
                    Text(
                        androidx.compose.ui.res.stringResource(
                            com.anindra.messages.R.string.search_filter_from, value
                        )
                    )
                }
            )
        }
        if (f.hasAttachment) {
            FilterChip(
                selected = true,
                onClick = { onRemove(SearchFilters.FilterKind.ATTACHMENTS) },
                label = {
                    Text(
                        androidx.compose.ui.res.stringResource(
                            com.anindra.messages.R.string.search_filter_attachments
                        )
                    )
                }
            )
        }
        f.before?.let { day ->
            FilterChip(
                selected = true,
                onClick = { onRemove(SearchFilters.FilterKind.BEFORE) },
                label = {
                    Text(
                        androidx.compose.ui.res.stringResource(
                            com.anindra.messages.R.string.search_filter_before, dayLabel(day)
                        )
                    )
                }
            )
        }
        f.after?.let { day ->
            FilterChip(
                selected = true,
                onClick = { onRemove(SearchFilters.FilterKind.AFTER) },
                label = {
                    Text(
                        androidx.compose.ui.res.stringResource(
                            com.anindra.messages.R.string.search_filter_after, dayLabel(day)
                        )
                    )
                }
            )
        }
    }
}

private fun dayLabel(day: com.anindra.messages.data.CalendarDayInput): String =
    "${day.year}/${day.month1}/${day.day}"

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
 * [resolved] query. Shared by both conversation lists.
 */
@Composable
fun rememberMessageMatchIds(vm: AppViewModel, resolved: ResolvedSearchQuery): Set<Long> {
    val held = remember { HeldMessageMatches() }
    var ids by remember { mutableStateOf(emptySet<Long>()) }
    // Keyed on the setting too: a toggle has to re-answer the same query, and
    // the visibility rule reads it on the repository side.
    val hideLinks = vm.settings.hideLinks
    LaunchedEffect(resolved, hideLinks) {
        // A number query has no message hits by definition — ConversationList.filter
        // matches it on address alone — so don't pay for the LIKE scan over every
        // message body. Clearing the held set too stops a previous text query's ids
        // surviving, which would over-include if a letter is typed mid-number.
        if (AddressIdentity.isNumberQuery(resolved.text)) {
            held.onQueryStarted("")
            ids = emptySet()
            return@LaunchedEffect
        }
        held.onQueryStarted(resolved.text)
        if (resolved.text.isBlank() && resolved.messageFilters == null) {
            ids = held.current()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        vm.conversationIdsMatchingMessage(resolved.text, hideLinks, resolved.messageFilters)
            .collect {
                held.onResultArrived(resolved.text, it)
                ids = held.current()
            }
    }
    return ids
}

/**
 * The conversations the list should show: inbox or archive, filtered by the
 * [resolved] query and sorted. Shared so the two lists cannot diverge again.
 */
@Composable
fun rememberSearchResults(
    conversations: List<Conversation>,
    showArchived: Boolean,
    resolved: ResolvedSearchQuery,
    unreadAtTop: Boolean,
    hideLinks: Boolean,
    messageMatchIds: Set<Long>
): List<Conversation> = remember(
    conversations, showArchived, resolved, unreadAtTop, hideLinks, messageMatchIds
) {
    // Ranked after the sort, not before: unread-at-top re-sorts the whole list,
    // so a contact put first beforehand would be moved back down by it.
    ConversationList.nameMatchesFirst(
        ConversationList.sort(
            ConversationList.filter(
                conversations, showArchived, resolved.text, messageMatchIds,
                { if (hideLinks) hideUrls(it.snippet) else it.snippet },
                snippetIsAMatch = resolved.messageFilters == null
            )
                .let { list ->
                    resolved.from?.let { from ->
                        list.filter { ConversationList.senderMatches(it, from) }
                    } ?: list
                },
            unreadAtTop, showArchived
        ),
        resolved.text
    )
}