package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reported: "in system dynamic the app icon lines are not showing".
 *
 * The themed (monochrome) layer reused the coloured foreground, which draws a
 * filled white bubble with blue strokes for the message lines. A themed icon
 * tints the entire drawable one colour, so the strokes merged into the fill and
 * the icon rendered as a blank shape. The monochrome layer is now line art:
 * outlined bubble plus lines, nothing filled over anything.
 */
class ThemedIconTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    @Test
    fun bothLauncherIconsPointAtTheLineArtLayer() {
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
    fun theLineArtLayerFillsNothing() {
        val mono = read("src/main/res/drawable/ic_launcher_monochrome.xml")
        assertTrue(
            "a filled shape would swallow the lines under a single tint",
            !mono.contains("android:fillColor=\"#FFFFFF\"")
        )
        assertTrue(
            "the bubble is a stroke",
            mono.contains("android:strokeWidth=\"3.2\"")
        )
        assertTrue(
            "and the three message lines are strokes too",
            Regex("""android:pathData="M24,28L48,28"""")
                .find(mono) != null
        )
    }

    @Test
    fun theColouredIconIsUnchanged() {
        val fg = read("src/main/res/drawable/ic_launcher_foreground.xml")
        assertTrue(
            "the normal launcher icon still uses the white bubble",
            fg.contains("android:fillColor=\"#FFFFFF\"")
        )
    }
}
