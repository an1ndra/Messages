package com.anindra.messages.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.anindra.messages.ui.avatarColor
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #347 (comment 4): under the dynamic (wallpaper) theme every contact landed on
 * the same primaryContainer colour, so nobody was distinguishable. The fill
 * must now come from the contact's own palette hue harmonized into the
 * wallpaper scheme — distinct per contact, hue-stable across night modes, and
 * always readable under its glyph.
 */
class DynamicAvatarHarmonizeTest {

    private val contacts = listOf(
        "+15551230001", "+15551230002", "+15551230003",
        "+15551230004", "+15551230005",
        "+15551230006", "+15551230007", "+15551230008"
    )

    private val wallpaperLight = lightColorScheme(primary = Color(0xFF6750A4))
    private val wallpaperDark = darkColorScheme(primary = Color(0xFFD0BCFF))

    @Test
    fun harmonizedFillsStayDistinctPerContact() {
        val fills = contacts.map { wallpaperLight.avatarFill(avatarColor(it)) }
        val distinct = fills.map { it.value }.toSet()
        assertTrue(
            "a harmonized palette must keep contacts distinguishable " +
                "(${distinct.size} distinct fills for ${contacts.size} contacts)",
            distinct.size >= contacts.size / 2
        )
    }

    @Test
    fun harmonizationKeepsTheSourceTone() {
        // Light->dark changes the resolved tone; hue must not move, so a
        // contact keeps its identity when the system flips night mode.
        contacts.forEach { key ->
            val source = avatarColor(key)
            val light = lightnessOf(wallpaperLight.avatarFill(source))
            val dark = lightnessOf(wallpaperDark.avatarFill(source))
            assertTrue(
                "$key: harmonization must not re-lighten per mode",
                abs(light - lightnessOf(source)) < 1e-3f &&
                    abs(dark - lightnessOf(source)) < 1e-3f
            )
        }
    }

    @Test
    fun huesDriftTowardTheWallpaperAccent() {
        val crimson = Color(0xFFFF0000)
        val crimsonHue = hueOf(crimson)
        val wallpaperHue = hueOf(wallpaperLight.primary)
        val before = hueDistance(crimsonHue, wallpaperHue)
        val after = hueDistance(hueOf(wallpaperLight.avatarFill(crimson)), wallpaperHue)
        assertTrue(
            "harmonized hue distance must shrink (before=$before, after=$after)",
            after < before
        )
        // But not fully: the contact keeps 75% of its own hue.
        assertTrue(after > 0.1f)
    }

    @Test
    fun aGreyscaleWallpaperKeepsTheSourceHue() {
        val neutral = lightColorScheme(primary = Color(0xFF9E9E9E))
        val crimson = Color(0xFFFF0000)
        assertTrue(abs(hueOf(neutral.avatarFill(crimson)) - hueOf(crimson)) < 1e-3f)
    }

    @Test
    fun glyphsStayReadableOnEveryHarmonizedFill() {
        listOf(wallpaperLight, wallpaperDark).forEach { scheme ->
            contacts.forEach { key ->
                val fill = scheme.avatarFill(avatarColor(key))
                val glyph = scheme.avatarOnFill(avatarColor(key))
                assertTrue(
                    "glyph $glyph on fill $fill must reach 3:1",
                    contrastRatio(glyph, fill) >= 3f
                )
            }
        }
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 1f
        return minOf(d, 1f - d)
    }
}
