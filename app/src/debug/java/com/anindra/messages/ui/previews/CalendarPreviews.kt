package com.anindra.messages.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.anindra.messages.data.CalendarSystem
import com.anindra.messages.ui.CalendarDatePicker

/**
 * The schedule grid (#363), which replaced Material's Gregorian-only picker.
 *
 * Showing the Jalali calendar is the point: a Gregorian render would not prove
 * the grid reads the user's choice at all.
 */
@PreviewLightDark
@Preview(name = "Schedule grid (Jalali)", showBackground = true, widthDp = 360, heightDp = 460)
@Composable
private fun CalendarDatePickerPreview() {
    Stage {
        CalendarDatePicker(
            utcMillis = SAMPLE_TS,
            calendar = CalendarSystem.PERSIAN,
            onPick = {}
        )
    }
}
