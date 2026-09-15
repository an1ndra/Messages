package com.anindra.messages.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontsTest {

    @Test
    fun mapsEachKeyToItsFamily() {
        assertEquals(DmSansFontFamily, AppFonts.familyFor(SettingsStore.FONT_DM_SANS))
        assertEquals(InterFontFamily, AppFonts.familyFor(SettingsStore.FONT_INTER))
        assertEquals(FigtreeFontFamily, AppFonts.familyFor(SettingsStore.FONT_FIGTREE))
        assertEquals(FontFamily.Default, AppFonts.familyFor(SettingsStore.FONT_SYSTEM))
    }

    @Test
    fun unknownKeyFallsBackToSystem() {
        assertEquals(FontFamily.Default, AppFonts.familyFor("nope"))
    }

    @Test
    fun optionsStartWithTheDefaultAndAreDistinct() {
        assertEquals(SettingsStore.FONT_DM_SANS, AppFonts.options.first())
        assertEquals(AppFonts.options.size, AppFonts.options.toSet().size)
    }
}
