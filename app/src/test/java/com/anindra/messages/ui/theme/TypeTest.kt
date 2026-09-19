package com.anindra.messages.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeTest {

    @Test
    fun everyTextStyleUsesTheGivenFamily() {
        val t = messagesTypography(FontFamily.Default)
        assertEquals(FontFamily.Default, t.displayLarge.fontFamily)
        assertEquals(FontFamily.Default, t.headlineLarge.fontFamily)
        assertEquals(FontFamily.Default, t.titleLarge.fontFamily)
        assertEquals(FontFamily.Default, t.bodyLarge.fontFamily)
        assertEquals(FontFamily.Default, t.bodyMedium.fontFamily)
        assertEquals(FontFamily.Default, t.labelLarge.fontFamily)
        assertEquals(FontFamily.Default, t.labelSmall.fontFamily)
    }

    @Test
    fun defaultTypographyUsesSystemFamily() {
        assertEquals(FontFamily.Default, messagesTypography().bodyLarge.fontFamily)
        assertEquals(FontFamily.Default, messagesTypography().headlineSmall.fontFamily)
    }

    @Test
    fun boldTypographyUsesBoldWeightForEveryRole() {
        val t = messagesTypography(bold = true)
        assertEquals(FontWeight.Bold, t.bodyLarge.fontWeight)
        assertEquals(FontWeight.Bold, t.bodyMedium.fontWeight)
        assertEquals(FontWeight.Bold, t.labelLarge.fontWeight)
        assertEquals(FontWeight.Bold, t.headlineSmall.fontWeight)
    }

    @Test
    fun nonBoldTypographyKeepsBaseWeights() {
        assertEquals(FontWeight.Normal, messagesTypography().bodyLarge.fontWeight)
    }
}
