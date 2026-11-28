package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "back to top" shortcut is only worth a button once the top is far enough
 * away that scrolling back is a chore: a list one flick from the top gets a
 * shortcut it does not need, sitting on the rows.
 */
class BackToTopTest {

    @Test
    fun aListAtTheTopOffersNoShortcut() {
        assertFalse(BackToTop.visible(firstVisibleIndex = 0))
    }

    @Test
    fun aListAFewRowsDownOffersNoShortcut() {
        assertFalse(BackToTop.visible(firstVisibleIndex = BackToTop.THRESHOLD - 1))
    }

    @Test
    fun theButtonIsDrawnAtSeventyPercent() {
        // It floats over the rows in the middle of the screen: fully opaque it
        // reads as a hole punched through the list.
        assertEquals(0.7f, BackToTop.ALPHA, 0.001f)
    }

    @Test
    fun aListScrolledPastTheThresholdOffersOne() {
        assertTrue(BackToTop.visible(firstVisibleIndex = BackToTop.THRESHOLD))
        assertTrue(BackToTop.visible(firstVisibleIndex = 40))
    }

    @Test
    fun theShortcutRisesFromTheBottomEdge() {
        // Diagnostics prints this label, and a script reads it: a 200 ms slide
        // is gone before uiautomator can dump the screen. Both must name the
        // same edge the composable's transition actually uses.
        assertEquals("from bottom", BackToTop.ENTRY_DESCRIPTION)
        val source = sourceOf("com/anindra/messages/ui/BackToTop.kt")
        assertTrue(source.contains("slideInVertically"))
        assertFalse(source.contains("slideInHorizontally"))
        assertTrue(source.contains("slideOutVertically"))
        assertFalse(source.contains("slideOutHorizontally"))
    }

    private fun sourceOf(relativePath: String): String {
        val workingDir = System.getProperty("user.dir") ?: "."
        var dir: File? = File(workingDir).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/$relativePath")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relativePath from $workingDir")
    }
}
