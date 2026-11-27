package com.anindra.messages.ui

import android.util.Log
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/** A result the user picked, still waiting for the destination screen to show it. */
data class SettingsJump(val rowRes: Int, val onHandled: () -> Unit)

val LocalSettingsJump = compositionLocalOf<SettingsJump?> { null }
val LocalSearchedSetting = compositionLocalOf<String?> { null }
val LocalSearchCardOffsets = compositionLocalOf<MutableMap<Int, Int>> { mutableMapOf() }

/** The first card on a settings screen, which is where searchable rows live. */
@Composable
fun SearchableSettingsCard(card: Int, content: @Composable () -> Unit) {
    val offsets = LocalSearchCardOffsets.current
    SettingsGroup(
        modifier = Modifier.onGloballyPositioned {
            offsets[card] = it.positionInParent().y.toInt()
        },
        content = content
    )
}

@Composable
fun SearchableGroupedRowList(
    card: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val offsets = LocalSearchCardOffsets.current
    GroupedRowList(
        modifier.onGloballyPositioned {
            offsets[card] = it.positionInParent().y.toInt()
        },
        content = content
    )
}

@Composable
private fun SettingsSearchJumpEffect(
    scrollState: ScrollState,
    cardOffsets: MutableMap<Int, Int>,
    setHighlight: (String?) -> Unit,
    onHandled: () -> Unit
) {
    val jump = LocalSettingsJump.current
    val title = jump?.rowRes?.let { stringResource(it) }
    LaunchedEffect(title) {
        val target = jump ?: return@LaunchedEffect
        for (attempt in 0 until 15) {
            val y = cardOffsets[0]
            if (y != null && scrollState.maxValue > 0) {
                scrollState.animateScrollTo(y.coerceAtLeast(0))
                break
            }
            delay(50)
        }
        Log.d("SettingsSearch", "jumped row=${target.rowRes} title=$title")
        setHighlight(title)
        delay(1400)
        setHighlight(null)
        onHandled()
    }
}

/** Gives a screen's rows a place to report their scroll offset and flash from. */
@Composable
fun ProvideSettingsSearchJump(
    scrollState: ScrollState,
    content: @Composable () -> Unit
) {
    val offsets = remember { mutableMapOf<Int, Int>() }
    val highlight = remember { mutableStateOf<String?>(null) }
    val jump = LocalSettingsJump.current
    SettingsSearchJumpEffect(
        scrollState = scrollState,
        cardOffsets = offsets,
        setHighlight = { highlight.value = it },
        onHandled = { jump?.onHandled?.invoke() }
    )
    CompositionLocalProvider(
        LocalSearchCardOffsets provides offsets,
        LocalSearchedSetting provides highlight.value,
        content = content
    )
}
