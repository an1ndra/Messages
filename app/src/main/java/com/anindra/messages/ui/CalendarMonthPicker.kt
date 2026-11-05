package com.anindra.messages.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anindra.messages.R
import com.anindra.messages.data.CalendarSystem
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Height of a day cell and of the week's letter above it; the whole grid is six. */
private val CellSize = 48.dp

/** Material draws the day inside its cell as a circle this wide. */
private val DayCircle = 40.dp

/**
 * The month grid the schedule sheet picks a day on, drawn in the calendar the
 * user chose (#363).
 *
 * Material's `DatePicker` only speaks Gregorian, so it could only be paired with
 * a hint line naming the same day in the reader's calendar. This draws that
 * calendar instead: Jalali, Hijri, Hebrew and the rest, thirteen-month years
 * included.
 *
 * The chrome is Material's, measured from `DatePicker.kt` in material3 1.3.2 —
 * a 120dp header, the picked day as the headline, a 56dp month/year row with
 * the chevrons on the right, 48dp cells around a 40dp circle, and the year
 * picker as an inline panel over the grid. Only the drawing is ours; a reader
 * who never changes the calendar sees what they saw before.
 *
 * What leaves here is unchanged from the platform picker's contract — midnight
 * UTC of the Gregorian day the cell falls on — so nothing downstream (see
 * [com.anindra.messages.data.ScheduledTime]) had to move.
 */
@Composable
internal fun CalendarDatePicker(
    utcMillis: Long?,
    calendar: CalendarSystem,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val months = remember(calendar, locale, zone) { IcuCalendarMonths(calendar, locale, zone) }
    var shown by remember(months) { mutableStateOf(months.monthOf(utcMillis)) }
    var choosingYear by remember { mutableStateOf(false) }
    val selected = remember(months, utcMillis) { utcMillis?.let { months.dayOf(it) } }
    val today = remember(months) { months.today() }

    Column(modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                // Material's header: title at the bottom-left of its own row, the
                // picked day underneath, 120dp from top to divider.
                .defaultMinSize(minHeight = 120.dp)
        ) {
            Text(
                stringResource(R.string.chat_select_date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 12.dp)
            )
            Text(
                utcMillis?.let {
                    formatDate(it, DateStyle.DayWithYear, calendar, locale, ZoneOffset.UTC)
                } ?: "",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 24.dp, bottom = 12.dp, end = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(
                horizontalArrangement = if (choosingYear) Arrangement.Start else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                TextButton(
                    onClick = { choosingYear = !choosingYear },
                    shape = CircleShape
                ) {
                    Text(shown.label)
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = stringResource(R.string.chat_schedule_choose_year),
                        modifier = Modifier.rotate(if (choosingYear) 180f else 0f)
                    )
                }
                // Material hides the chevrons while the year panel is open: the
                // panel scrolls instead, and the row keeps one control.
                if (!choosingYear) {
                    Row {
                        IconButton(onClick = { shown = months.shiftMonth(shown, -1) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription =
                                    stringResource(R.string.chat_schedule_previous_month)
                            )
                        }
                        IconButton(onClick = { shown = months.shiftMonth(shown, 1) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription =
                                    stringResource(R.string.chat_schedule_next_month)
                            )
                        }
                    }
                }
            }

            if (choosingYear) {
                val first = shown.year - 100
                val years = remember(shown.year) { (first..shown.year + 100).toList() }
                val currentYear = today.year
                val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = 99)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CellSize * 7 - 1.dp)
                        .semantics { paneTitle = shown.label }
                ) {
                    items(years) { year ->
                        val isShown = year == shown.year
                        val isCurrent = year == currentYear
                        val container by animateColorAsState(
                            if (isShown) MaterialTheme.colorScheme.primary else Color.Transparent,
                            tween(100),
                            label = "yearContainer"
                        )
                        Surface(
                            selected = isShown,
                            onClick = {
                                // A Hebrew or Coptic year runs to thirteen
                                // months, so the month on screen may not exist
                                // in the year just picked.
                                val firstMonth = months.month(year, 0)
                                shown = months.month(
                                    year,
                                    minOf(shown.month, firstMonth.monthCount - 1)
                                )
                                choosingYear = false
                            },
                            shape = CircleShape,
                            color = container,
                            contentColor = when {
                                isShown -> MaterialTheme.colorScheme.onPrimary
                                isCurrent -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            border = if (isCurrent && !isShown) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                            modifier = Modifier.size(width = 72.dp, height = 36.dp)
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    months.numberLabel(year),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            } else {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = CellSize)
                ) {
                    shown.weekdayLabels.forEach { label ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.requiredSize(CellSize)
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Column(
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().height(CellSize * 6)
                ) {
                    shown.weeks.forEach { week ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            week.forEach { day ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.requiredSize(CellSize)
                                ) {
                                    if (day == null) return@Box
                                    val isSelected = selected != null &&
                                        selected.year == shown.year &&
                                        selected.month == shown.month &&
                                        selected.day == day
                                    val isToday = today.year == shown.year &&
                                        today.month == shown.month &&
                                        today.day == day
                                    val cellMillis = months.utcMillisOf(shown.year, shown.month, day)
                                    val spoken = formatDate(
                                        cellMillis,
                                        DateStyle.DayWithFullWeekdayAndYear,
                                        calendar,
                                        locale,
                                        ZoneOffset.UTC
                                    )
                                    // Material's day: a 40dp circle inside the
                                    // 48dp cell, filled `primary` when selected,
                                    // ringed `primary` for today, with the
                                    // selection fading in over 100ms.
                                    val container by animateColorAsState(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent,
                                        tween(100),
                                        label = "dayContainer"
                                    )
                                    Surface(
                                        selected = isSelected,
                                        onClick = { onPick(cellMillis) },
                                        shape = CircleShape,
                                        color = container,
                                        contentColor = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            isToday -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        border = if (isToday && !isSelected) {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            null
                                        },
                                        modifier = Modifier
                                            .size(DayCircle)
                                            .semantics { contentDescription = spoken }
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                shown.dayLabels[day - 1],
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
