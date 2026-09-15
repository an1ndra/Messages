package com.anindra.messages.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.anindra.messages.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontsTest {

    @Test
    fun mapsEachKeyToItsFamily() {
        assertEquals(FontFamily.Default, AppFonts.familyFor(SettingsStore.FONT_SYSTEM))
        assertEquals(DmSansFontFamily, AppFonts.familyFor(SettingsStore.FONT_DM_SANS))
        assertEquals(InterFontFamily, AppFonts.familyFor(SettingsStore.FONT_INTER))
        assertEquals(FigtreeFontFamily, AppFonts.familyFor(SettingsStore.FONT_FIGTREE))
        assertEquals(PoppinsFontFamily, AppFonts.familyFor(SettingsStore.FONT_POPPINS))
    }

    @Test
    fun unknownKeyFallsBackToSystem() {
        assertEquals(FontFamily.Default, AppFonts.familyFor("nope"))
    }

    @Test
    fun optionsStartWithSystemAndAreDistinct() {
        assertEquals(SettingsStore.FONT_SYSTEM, AppFonts.options.first())
        assertEquals(5, AppFonts.options.size)
        assertEquals(AppFonts.options.size, AppFonts.options.toSet().size)
    }
}
