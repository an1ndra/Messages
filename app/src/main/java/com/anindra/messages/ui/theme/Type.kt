package com.anindra.messages.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.anindra.messages.R

/**
 * Inter (SIL OFL, bundled in res/font) as a close, freely-licensed stand-in for
 * Google Sans, which is proprietary and cannot be redistributed.
 */
val MessagesFontFamily: FontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

fun messagesTypography(
    family: FontFamily = MessagesFontFamily,
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

val MessagesTypography: Typography = messagesTypography()
