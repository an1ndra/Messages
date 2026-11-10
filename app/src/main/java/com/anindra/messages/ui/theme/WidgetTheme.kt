package com.anindra.messages.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.glance.color.ColorProvider as dayNightColorProvider
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders as material3ColorProviders
import androidx.glance.unit.ColorProvider
import com.anindra.messages.data.SettingsStore
import com.anindra.messages.ui.avatarColor
import com.anindra.messages.ui.avatarGlyphInk

/**
 * The (day, night) schemes a widget paints with. A fixed theme mode resolves to
 * the same scheme on both sides, so the launcher's night setting cannot change
 * a widget the app has pinned to light, dark or AMOLED.
 */
internal fun widgetSchemes(
    mode: String,
    a11y: A11yOptions,
    dynamicLight: ColorScheme? = null,
    dynamicDark: ColorScheme? = null
): Pair<ColorScheme, ColorScheme> =
    schemeFor(mode, a11y, darkTheme = themeIsDark(mode, false), dynamic = dynamicLight) to
        schemeFor(mode, a11y, darkTheme = themeIsDark(mode, true), dynamic = dynamicDark)

internal data class WidgetColors(
    val providers: ColorProviders,
    val day: ColorScheme,
    val night: ColorScheme
) {
    val avatarInk: ColorProvider
        get() = dayNightColorProvider(day = avatarGlyphInk(day), night = avatarGlyphInk(night))

    fun avatarFill(key: String): ColorProvider {
        val fill = avatarColor(key)
        return dayNightColorProvider(day = fill, night = fill)
    }
}

internal fun widgetColors(context: Context, settings: SettingsStore): WidgetColors {
    val mode = settings.themeMode
    val dynamic = mode == SettingsStore.THEME_DYNAMIC &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val (day, night) = widgetSchemes(
        mode = mode,
        a11y = settings.a11yOptions(),
        dynamicLight = if (dynamic) dynamicLightColorScheme(context) else null,
        dynamicDark = if (dynamic) dynamicDarkColorScheme(context) else null
    )
    return WidgetColors(material3ColorProviders(day, night), day, night)
}
