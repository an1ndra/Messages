package com.anindra.messages.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anindra.messages.R
import com.anindra.messages.data.CalendarSystem
import java.text.Collator
import java.util.Locale

/**
 * The calendar picker (#363).
 *
 * It lives in its own composable rather than inline in the Advanced screen for
 * the same reason [com.anindra.messages.diagnostics.DiagnosticsDialog] does: the
 * list is longer than the space a dialog gets on a short screen, so it scrolls,
 * and a route screen must never create its own scroll state (see
 * SettingsScrollStateTest).
 */
@Composable
fun CalendarPickerDialog(
    choice: CalendarSystem,
    onChoose: (CalendarSystem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val options = calendarOptions { calendarLabelRes(it, context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_choose_calendar)) },
        text = {
            // Nine calendars do not fit an alert dialog on a short screen, and
            // a clipped list would hide the ones that sort last.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(option) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = choice == option,
                            onClick = { onChoose(option) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(calendarLabelRes(option, context))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/**
 * Automatic first — it is the default, and it is not a calendar — then the rest
 * alphabetically by the label the reader actually sees. Sorting on the enum name
 * would put the same order in every language, most of them wrong: a French
 * reader expects "Bouddhiste, Copte, Grégorien…", not "Grégorien, Persan,
 * Islamique…". The [Collator] is what makes "Hébreu" sort under H.
 */
internal fun calendarOptions(
    locale: Locale = Locale.getDefault(),
    label: (CalendarSystem) -> String
): List<CalendarSystem> {
    val collator = Collator.getInstance(locale)
    return listOf(CalendarSystem.AUTO) +
        (CalendarSystem.entries - CalendarSystem.AUTO)
            .sortedWith { a, b -> collator.compare(label(a), label(b)) }
}

internal fun calendarLabelRes(system: CalendarSystem, context: android.content.Context): String =
    context.getString(
        when (system) {
            CalendarSystem.AUTO -> R.string.calendar_auto
            CalendarSystem.GREGORIAN -> R.string.calendar_gregorian
            CalendarSystem.PERSIAN -> R.string.calendar_persian
            CalendarSystem.HIJRI_UMALQURA -> R.string.calendar_hijri
            CalendarSystem.HEBREW -> R.string.calendar_hebrew
            CalendarSystem.BUDDHIST -> R.string.calendar_buddhist
            CalendarSystem.JAPANESE -> R.string.calendar_japanese
            CalendarSystem.INDIAN -> R.string.calendar_indian
            CalendarSystem.COPTIC -> R.string.calendar_coptic
        }
    )

/** The subtitle shows what will render, so "Automatic" names the calendar it
 *  picked: "Automatic · Persian (Jalali)" rather than a bare "Automatic". */
internal fun calendarLabel(
    choice: CalendarSystem,
    resolved: CalendarSystem,
    context: android.content.Context
): String {
    val resolvedLabel = calendarLabelRes(resolved, context)
    return if (choice == CalendarSystem.AUTO) {
        "${context.getString(R.string.calendar_auto)} \u00B7 $resolvedLabel"
    } else {
        resolvedLabel
    }
}
