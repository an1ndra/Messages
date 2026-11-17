package com.anindra.messages.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity

/** What the platform says the keyboard covers.
 *
 *  The reported IME inset is a claim, and some OEM builds lie: MIUI with an
 *  enlarged display size hands an edge-to-edge window an inset far larger than
 *  the keyboard, which made the composer's padding reserve nearly the whole
 *  screen — the input row ended up pinned to the top of the window with the
 *  reserved height blank below it. */
object ImeGuard {

    /** Debug builds only: set from a launch intent extra, this is read instead
     *  of the platform's reported inset — the standing way to reproduce an
     *  OEM's dishonest report on an emulator whose own report is honest. */
    @Volatile
    var debugReportedBottomPx: Int? = null

    /** The inset to apply: the platform's report, or the debug stand-in. */
    fun reportedBottomPx(platformReportedPx: Int): Int =
        debugReportedBottomPx ?: platformReportedPx
}

/** The composer's bottom padding, driven by the reported IME inset. */
@Composable
fun Modifier.imeGuardedPadding(): Modifier {
    val density = LocalDensity.current
    val reported = ImeGuard.reportedBottomPx(WindowInsets.ime.getBottom(density))
    return this.then(
        Modifier.padding(bottom = with(density) { reported.toDp() })
    )
}
