package com.anindra.messages.ui

import androidx.compose.ui.graphics.Color

/** Icon tints that have to differ between light and dark. */
object IconTint {
    /** A disabled icon keeps the same hue but loses contrast against the surface.
     *  A fixed fraction does not work for both: onSurfaceVariant is dark in light
     *  mode and light in dark mode, so 38% over near-black falls well under the 3:1
     *  that UI components need and the icon reads as missing rather than disabled.
     *  Dark mode needs a much higher fraction to land in the same place. */
    fun disabledAlpha(isDark: Boolean): Float = if (isDark) 0.62f else 0.38f

    fun disabled(contentColor: Color, isDark: Boolean): Color =
        contentColor.copy(alpha = disabledAlpha(isDark))
}
