package com.anindra.messages.ui.legacy

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.anindra.messages.ui.hexColor

/**
 * The legacy settings sheet: one page colour for every legacy settings screen.
 *
 * Reported: "in old UI General settings and Advanced setting background colour is
 * different". Only General painted the sheet; Advanced and Accessibility fell
 * back to the theme background, so opening them changed the page colour under
 * the rows.
 *
 * The colour lives here rather than in each screen so a screen cannot forget it,
 * and this composable is also what reports it: a per-screen report that read
 * `MaterialTheme.colorScheme.surfaceContainer` itself kept passing after the
 * Scaffold's colour was removed, because it described the role instead of what
 * was painted. A screen that bypasses this scaffold now reports nothing at all,
 * which is what scripts/test-legacy-settings-colours.sh fails on.
 */
@Composable
fun LegacySettingsScaffold(
    screen: String,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val sheet = MaterialTheme.colorScheme.surfaceContainer
    val row = MaterialTheme.colorScheme.background
    LaunchedEffect(sheet, row) {
        Log.d(
            "SettingsColors",
            "screen=$screen page=${hexColor(sheet)} row=${hexColor(row)}"
        )
    }
    Scaffold(containerColor = sheet, topBar = topBar, content = content)
}
