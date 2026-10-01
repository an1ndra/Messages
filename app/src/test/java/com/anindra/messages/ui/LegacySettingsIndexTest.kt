package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Search on the legacy settings screen works by name: pick a result and land on
 * the card it lives in. That needs an index from option to card, and the index is
 * a hand-written copy of what the screen renders. An option added to the screen
 * but left out of the index would simply be unsearchable, and a renumbered card
 * would scroll to the wrong place. Neither shows up in a diff, so the index is
 * checked against the source here.
 */
class LegacySettingsIndexTest {

    private val main = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .firstOrNull { it.isDirectory }
        ?: error("app/src/main not found")

    private val screen = File(
        main, "java/com/anindra/messages/ui/legacy/LegacySettingsScreen.kt"
    ).readText()

    private val index = File(main, "java/com/anindra/messages/ui/SettingsSearch.kt").readText()

    /**
     * Option titles the settings *list* renders. Bounded to the scrolling Column
     * so rows belonging to dialogs further down the file are not counted -- those
     * are not reachable by scrolling and must not appear in search results.
     */
    private val listedRows: Set<String>
        get() {
            val body = screen.substringAfter(".verticalScroll(scrollState)")
            // The list ends where the dialogs start; rows inside those are not
            // reachable by scrolling and so must not be searchable.
            val end = body.indexOf("\n    if (")
            val region = if (end > 0) body.substring(0, end) else body
            // \b matters: without it "subtitle = stringResource(" matches too.
            return Regex("""\btitle = stringResource\(R\.string\.(\w+)\)""")
                .findAll(region)
                .map { it.groupValues[1] }
                .toSet()
        }

    private val indexedRows: Set<String>
        get() = Regex("""R\.string\.(\w+)""").findAll(index).map { it.groupValues[1] }.toSet()

    @Test
    fun everyOptionTheListRendersIsSearchable() {
        val missing = listedRows - indexedRows
        assertTrue("these rows cannot be found by search: $missing", missing.isEmpty())
    }

    @Test
    fun theIndexNamesNothingTheListDoesNotRender() {
        val phantom = indexedRows - listedRows
        assertTrue("index names options that do not exist: $phantom", phantom.isEmpty())
    }

    @Test
    fun everySearchableOptionSitsInACard() {
        val groups = SettingsSearch.LegacySettingsIndex.GROUPS
        val all = SettingsSearch.LegacySettingsIndex.ALL
        assertEquals(all.size, all.distinct().size)
        for (res in all) {
            assertTrue(
                "option $res belongs to no card",
                SettingsSearch.LegacySettingsIndex.groupOf(res) in groups.indices
            )
            assertEquals(1, groups.count { res in it })
        }
    }

    @Test
    fun noCardIsEmpty() {
        SettingsSearch.LegacySettingsIndex.GROUPS.forEachIndexed { i, group ->
            assertTrue("card $i is empty", group.isNotEmpty())
        }
    }

    @Test
    fun anOptionOutsideTheIndexHasNoCard() {
        assertEquals(-1, SettingsSearch.LegacySettingsIndex.groupOf(-1))
    }
}