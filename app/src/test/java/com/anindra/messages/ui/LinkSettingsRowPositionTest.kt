package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Link behaviour is a three-row group: "Hide links from messages" (first),
 * "Highlight links" (middle) and "Link open warning" (last). "Highlight links"
 * once took a `LAST` position whenever hiding was off, so its bottom corners
 * rounded to the group's outer radius while it still sat above the warning row
 * — the odd, over-rounded row the user reported. Its position must not depend
 * on `hideLinks`; it is always the middle row.
 */
class LinkSettingsRowPositionTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val linkScreen: String by lazy {
        val source = File(main, "java/com/anindra/messages/ui/AdvancedSubScreens.kt").readText()
        val start = source.indexOf("fun LinkSettingsScreen(")
        assertTrue("LinkSettingsScreen not found", start >= 0)
        val end = source.indexOf("SettingsFooter", start)
        assertTrue("could not find the end of the LinkSettingsScreen group", end > start)
        source.substring(start, end)
    }

    private fun positionBefore(anchor: String, window: Int = 220): String {
        val at = linkScreen.indexOf(anchor)
        assertTrue("$anchor not found in LinkSettingsScreen", at >= 0)
        return linkScreen.substring((at - window).coerceAtLeast(0), at)
    }

    @Test
    fun theGroupHasExactlyThreeRows() {
        assertEquals(
            "Link behaviour is one group of three rows",
            3,
            linkScreen.windowed("SettingsRow(".length).count { it == "SettingsRow(" }
        )
    }

    @Test
    fun highlightingIsAlwaysTheMiddleRow() {
        val before = positionBefore("R.string.settings_advanced_highlight_links")
        assertTrue(
            "Highlight links must be a MIDDLE row so its corners stay inner",
            before.contains("position = RowPosition.MIDDLE")
        )
        assertTrue(
            "Highlight links must not take a LAST position when hiding is off",
            !before.contains("RowPosition.LAST")
        )
    }

    @Test
    fun hidingIsFirstAndTheWarningIsLast() {
        assertTrue(
            "'Hide links from messages' must open the group",
            positionBefore("R.string.settings_advanced_hide_links").contains("position = RowPosition.FIRST")
        )
        assertTrue(
            "'Link open warning' must close the group",
            positionBefore("R.string.settings_advanced_link_warning").contains("position = RowPosition.LAST")
        )
    }
}
