package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The themed (monochrome) icon layer.
 *
 * Two rounds of feedback shaped it. First: "in system dynamic the app icon lines
 * are not showing" — the layer had been pointed at the coloured foreground,
 * whose blue strokes for the message lines merged into its white fill once the
 * launcher tinted everything one colour, and the icon rendered as a blank shape.
 * It was changed to line art, which then read as "the fill is gone"; the
 * reviewed decision was A, a filled bubble with the lines cut out of it.
 *
 * A monochrome layer is a mask: the launcher tints opaque pixels one colour and
 * the plate shows through everything else. So the fill has to be a fill and the
 * lines have to be holes — a stroke would be opaque and would disappear into it
 * again. That is what these assertions protect.
 */
class ThemedIconTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val monochrome by lazy { read("src/main/res/drawable/ic_launcher_monochrome.xml") }

    @Test
    fun bothLauncherIconsPointAtTheMonochromeLayer() {
        listOf(
            "src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            "src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"
        ).forEach { path ->
            assertTrue(
                "$path must use the monochrome drawable",
                read(path).contains("android:drawable=\"@drawable/ic_launcher_monochrome\"")
            )
        }
    }

    @Test
    fun theBubbleIsFilledAndTheLinesAreHoles() {
        assertTrue("the bubble must be filled", monochrome.contains("android:fillColor="))
        assertEquals(
            "the lines only read as light if they are cut out of the fill",
            "evenOdd",
            Regex("""android:fillType="(\w+)"""").find(monochrome)?.groupValues?.get(1)
        )
        // Three capsules, each closed by two half-circle ends.
        assertEquals(
            "expected three line capsules inside the bubble path",
            6,
            Regex("A1\\.25,1\\.25 0 0 1").findAll(monochrome).count()
        )
        assertFalse(
            "an opaque stroke would merge into the fill, which is the original bug",
            monochrome.contains("android:strokeColor")
        )
    }

    @Test
    fun theLineCapsulesSitOnTheColouredIconsLinePositions() {
        // Same y positions and lengths as the strokes in the coloured icon, so
        // the two faces of the app show the same three lines.
        for (y in listOf("26.75", "32.75", "38.75")) {
            assertTrue("no line capsule starting at y=$y", monochrome.contains("M24,$y"))
        }
        assertTrue(monochrome.contains("L48,26.75"))
        assertTrue(monochrome.contains("L44,32.75"))
        assertTrue(monochrome.contains("L38,38.75"))
    }

    @Test
    fun theColouredIconIsUnchanged() {
        val fg = read("src/main/res/drawable/ic_launcher_foreground.xml")
        assertTrue(
            "the normal launcher icon still uses the white bubble",
            fg.contains("android:fillColor=\"#FFFFFF\"")
        )
        assertTrue(
            "and its blue message lines",
            fg.contains("android:strokeColor=\"#0B57D0\"")
        )
    }
}
