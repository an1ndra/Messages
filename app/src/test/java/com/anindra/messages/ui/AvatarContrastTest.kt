package com.anindra.messages.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.anindra.messages.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette is GM's, but a chip that cannot be told apart from the page is a
 * chip in name only: pink on white is 2.58:1, cyan 1.78:1, and a muted wallpaper
 * palette leaves brown/indigo/blue-grey matching the page in hue *and* lightness.
 *
 * So a contact keeps its own colour when that colour clears 3:1 against the
 * surface, and otherwise takes the nearest one that does.
 */
class AvatarContrastTest {

    private val surfaces = mapOf(
        "white" to Color(0xFFFFFFFF),
        "seeded light" to Color(0xFFF8F9FC),
        "seeded dark" to Color(0xFF131314),
        "dynamic dark" to Color(0xFF0D1419),
        "dynamic dark 2" to Color(0xFF242B30),
        "bubble" to Color(0xFF333537)
    )

    private val keys = listOf(
        "555-1212", "555-4788", "(650) 555-1212", "+1555444329",
        "ALICE", "bob", "DK-AIRCEL", "VM-HDFCBK", "999", "group-1"
    )

    private fun hex(c: Color) = "#%02X%02X%02X".format(
        (c.red * 255f + 0.5f).toInt(), (c.green * 255f + 0.5f).toInt(), (c.blue * 255f + 0.5f).toInt()
    )

    @Test
    fun everyChipClearsTheFloorOnEverySurface() {
        surfaces.forEach { (name, surface) ->
            keys.forEach { key ->
                val fill = avatarColorOn(key, surface)
                val ratio = contrastRatio(fill, surface)
                assertTrue(
                    "$key on $name: ${hex(fill)} is ${"%.2f".format(ratio)}:1, below 3:1",
                    ratio >= AVATAR_MIN_CONTRAST
                )
            }
        }
    }

    @Test
    fun aColourThatAlreadyPassesIsKept() {
        // Deep purple on white is 10:1; the contact must not be repainted.
        val surface = Color(0xFFFFFFFF)
        keys.forEach { key ->
            val own = avatarColor(key)
            if (contrastRatio(own, surface) >= AVATAR_MIN_CONTRAST) {
                assertEquals("$key was repainted although it passes", own, avatarColorOn(key, surface))
            }
        }
    }

    @Test
    fun theSameContactKeepsTheSameColour() {
        surfaces.forEach { (name, surface) ->
            keys.forEach { key ->
                assertEquals(
                    "selection must be deterministic on $name",
                    avatarColorOn(key, surface),
                    avatarColorOn(key, surface)
                )
            }
        }
    }

    @Test
    fun lowChromaColoursAreReplacedOnDarkSurfaces() {
        // Brown (#795548) measures 2.83:1 on #0D1419 and 2.19:1 on #242B30.
        val dark = Color(0xFF0D1419)
        val brownKeys = keys.filter { avatarColor(it) == Color(0xFF795548) }
        brownKeys.forEach { key ->
            assertNotEquals(
                "$key kept brown, which blends into the dark page",
                Color(0xFF795548),
                avatarColorOn(key, dark)
            )
        }
    }

    @Test
    fun theInkIsChosenFromTheFillNotTheNightMode() {
        // White ink on pink is 2.4:1; the near-black is 8.8:1. The ink must
        // follow the fill, so a light theme does not paint white on pastel.
        assertEquals("#202124", hex(avatarInkOn(Color(0xFFFF63B8))))
        assertEquals("#202124", hex(avatarInkOn(Color(0xFF4ECDE6))))
        // A dark fill still needs white.
        assertEquals("#FFFFFF", hex(avatarInkOn(Color(0xFF1A46A0))))
    }

    @Test
    fun theInkIsReadableOnEveryFillThePaletteCanProduce() {
        // 3:1 is the floor for a graphical element (WCAG 1.4.11). It cannot be
        // 4.5: the mid-tone chips top out around 4.3 whichever ink is used, so
        // the rule is "whichever reads better", not "always 4.5".
        surfaces.forEach { (name, surface) ->
            keys.forEach { key ->
                val fill = avatarColorOn(key, surface)
                val ink = avatarInkOn(fill)
                val ratio = contrastRatio(ink, fill)
                assertTrue(
                    "$key on $name: ink ${hex(ink)} on ${hex(fill)} is " +
                        "${"%.2f".format(ratio)}:1",
                    ratio >= 3f
                )
                assertTrue(
                    "$key on $name: the better of the two inks was not chosen",
                    ratio >= maxOf(
                        contrastRatio(Color.White, fill),
                        contrastRatio(Color(0xFF202124), fill)
                    ) - 0.01f
                )
            }
        }
    }

    @Test
    fun theSeededSchemesStillLookLikeGoogleMessages() {
        // The palette must not be rewritten: most contacts on the dark scheme
        // keep their own colour, which is what makes them look like GM.
        val dark = darkColorScheme(background = Color(0xFF131314))
        val kept = keys.count { avatarColorOn(it, dark.background) == avatarColor(it) }
        assertTrue("dark kept only $kept of ${keys.size}", kept >= keys.size - 4)
    }
}
