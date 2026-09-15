@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.anindra.messages.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.anindra.messages.R
import com.anindra.messages.data.SettingsStore

/** Maps the 400/500/600/700 weights of a variable font to a [FontFamily]. */
private fun variableFont(resId: Int): FontFamily = FontFamily(
    Font(resId, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(resId, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(resId, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(resId, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

/**
 * Bundled UI fonts (SIL OFL) — free stand-ins for Google Sans, which is
 * proprietary and cannot be redistributed. All are variable fonts in res/font.
 */
val DmSansFontFamily: FontFamily = variableFont(R.font.dm_sans)
val InterFontFamily: FontFamily = variableFont(R.font.inter)
val FigtreeFontFamily: FontFamily = variableFont(R.font.figtree)

object AppFonts {
    /** Selectable fonts, in picker order (default first). */
    val options: List<String> = listOf(
        SettingsStore.FONT_DM_SANS,
        SettingsStore.FONT_INTER,
        SettingsStore.FONT_FIGTREE,
        SettingsStore.FONT_SYSTEM
    )

    fun familyFor(key: String): FontFamily = when (key) {
        SettingsStore.FONT_DM_SANS -> DmSansFontFamily
        SettingsStore.FONT_INTER -> InterFontFamily
        SettingsStore.FONT_FIGTREE -> FigtreeFontFamily
        else -> FontFamily.Default
    }
}

fun messagesTypography(
    family: FontFamily = DmSansFontFamily,
    base: Typography = Typography()
): Typography = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = family),
    displayMedium = base.displayMedium.copy(fontFamily = family),
    displaySmall = base.displaySmall.copy(fontFamily = family),
    headlineLarge = base.headlineLarge.copy(fontFamily = family),
    headlineMedium = base.headlineMedium.copy(fontFamily = family),
    headlineSmall = base.headlineSmall.copy(fontFamily = family),
    titleLarge = base.titleLarge.copy(fontFamily = family),
    titleMedium = base.titleMedium.copy(fontFamily = family),
    titleSmall = base.titleSmall.copy(fontFamily = family),
    bodyLarge = base.bodyLarge.copy(fontFamily = family),
    bodyMedium = base.bodyMedium.copy(fontFamily = family),
    bodySmall = base.bodySmall.copy(fontFamily = family),
    labelLarge = base.labelLarge.copy(fontFamily = family),
    labelMedium = base.labelMedium.copy(fontFamily = family),
    labelSmall = base.labelSmall.copy(fontFamily = family)
)
