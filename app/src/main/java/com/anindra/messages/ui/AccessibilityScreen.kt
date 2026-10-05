package com.anindra.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anindra.messages.AppViewModel
import com.anindra.messages.R
import com.anindra.messages.ui.theme.A11yOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    scrollState: ScrollState = rememberScrollState()
) {
    BackHandler(onBack = onBack)

    var fontDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accessibility_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.icon_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
.verticalScroll(scrollState)
                .padding(horizontal = SettingsLayout.SCREEN_PADDING)
        ) {
            Spacer(Modifier.height(SettingsLayout.TOP_GAP))

            SettingsGroup {
                A11Y_OPTION_ROWS.forEach { (option, position) ->
                    when (option) {
                        A11yOption.FONT_SIZE -> SettingsRow(
                            position = position,
                            title = stringResource(R.string.a11y_font_size_title),
                            subtitle = fontScaleLabel(vm.a11yFontScalePercent),
                            onClick = { fontDialog = true }
                        )
                        A11yOption.BOLD -> SettingsRow(
                            position = position,
                            title = stringResource(R.string.a11y_bold_title),
                            checked = vm.a11yBold,
                            onChecked = { vm.a11yBold = it }
                        )
                        A11yOption.HIGH_CONTRAST -> SettingsRow(
                            position = position,
                            title = stringResource(R.string.a11y_high_contrast_title),
                            checked = vm.a11yHighContrast,
                            onChecked = { vm.a11yHighContrast = it }
                        )
                        A11yOption.LARGE_TOUCH -> SettingsRow(
                            position = position,
                            title = stringResource(R.string.a11y_large_touch_title),
                            checked = vm.a11yLargeTouch,
                            onChecked = { vm.a11yLargeTouch = it }
                        )
                        A11yOption.REDUCE_MOTION -> SettingsRow(
                            position = position,
                            title = stringResource(R.string.a11y_reduce_motion_title),
                            checked = vm.a11yReduceMotion,
                            onChecked = { vm.a11yReduceMotion = it }
                        )
                    }
                }
            }

            SettingsFooter(stringResource(R.string.settings_accessibility_footer))
        }
    }

    if (fontDialog) {
        var selected by remember { mutableIntStateOf(vm.a11yFontScalePercent) }
        AlertDialog(
            onDismissRequest = { fontDialog = false },
            title = { Text(stringResource(R.string.a11y_choose_font_size)) },
            text = {
                Column {
                    A11yOptions.PERCENT_OPTIONS.forEach { percent ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = percent }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selected == percent,
                                onClick = { selected = percent }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(fontScaleLabel(percent))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.a11yFontScalePercent = selected
                    fontDialog = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { fontDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun fontScaleLabel(percent: Int): String = when (percent) {
    85 -> stringResource(R.string.a11y_font_85)
    115 -> stringResource(R.string.a11y_font_115)
    130 -> stringResource(R.string.a11y_font_130)
    else -> stringResource(R.string.a11y_font_100)
}
