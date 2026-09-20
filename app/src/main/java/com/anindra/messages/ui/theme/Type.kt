@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.anindra.messages.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
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
val PoppinsFontFamily: FontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

object AppFonts {
    /** Selectable fonts, in picker order (default first). */
    val options: List<String> = listOf(
        SettingsStore.FONT_SYSTEM,
        SettingsStore.FONT_DM_SANS,
        SettingsStore.FONT_INTER,
        SettingsStore.FONT_FIGTREE,
        SettingsStore.FONT_POPPINS
    )

    fun familyFor(key: String): FontFamily = when (key) {
        SettingsStore.FONT_DM_SANS -> DmSansFontFamily
        SettingsStore.FONT_INTER -> InterFontFamily
        SettingsStore.FONT_FIGTREE -> FigtreeFontFamily
        SettingsStore.FONT_POPPINS -> PoppinsFontFamily
        else -> FontFamily.Default
    }
}

fun messagesTypography(
    family: FontFamily = FontFamily.Default,
    base: Typography = Typography(),
    bold: Boolean = false
): Typography {
    fun style(s: TextStyle): TextStyle =
        s.copy(fontFamily = family, fontWeight = if (bold) FontWeight.Bold else s.fontWeight)
    return base.copy(
        displayLarge = style(base.displayLarge),
        displayMedium = style(base.displayMedium),
        displaySmall = style(base.displaySmall),
        headlineLarge = style(base.headlineLarge),
        headlineMedium = style(base.headlineMedium),
        headlineSmall = style(base.headlineSmall),
        titleLarge = style(base.titleLarge),
        titleMedium = style(base.titleMedium),
        titleSmall = style(base.titleSmall),
        bodyLarge = style(base.bodyLarge),
        bodyMedium = style(base.bodyMedium),
        bodySmall = style(base.bodySmall),
        labelLarge = style(base.labelLarge),
        labelMedium = style(base.labelMedium),
        labelSmall = style(base.labelSmall)
    )
}
