package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Search on the legacy settings tree works by name: pick a result and land on
 * the card it lives in, scrolling and flashing the row. That needs an index from
 * option to card and screen, and the index is a hand-written copy of what the
 * screens render. An option added to a screen but left out of the index would
 * simply be unsearchable, and a renumbered card would scroll to the wrong place.
 * Neither shows up in a diff, so the index is checked against the source here.
 */
class LegacySettingsIndexTest {

    private val main = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .firstOrNull { it.isDirectory }
        ?: error("app/src/main not found")

    private val index = File(main, "java/com/anindra/messages/ui/SettingsSearch.kt").readText()

    /** Screens whose rows are searchable, and the file each renders in. */
    private val screens = listOf(
        Triple("settings", "legacy/LegacySettingsScreen.kt", "General"),
        Triple("advanced", "legacy/LegacyAdvancedSettingsScreen.kt", "Advanced"),
        Triple("accessibility", "legacy/LegacyAccessibilityScreen.kt", "Accessibility")
    )

    private fun source(file: String) = File(main, "java/com/anindra/messages/ui/$file").readText()

    /** Row titles each screen renders as `stringResource(R.string.X)`. */
    private fun renderedTitles(file: String): Set<String> {
        val body = source(file)
        val from = body.substringAfter(".verticalScroll(")
        val end = from.indexOf("\n    if (")
        val region = if (end > 0) from.substring(0, end) else from
        return Regex("""\btitle = stringResource\(R\.string\.(\w+)\)""")
            .findAll(region)
            .map { it.groupValues[1] }
            .toSet()
    }

    private val indexedNames: Set<String>
        get() = Regex("""R\.string\.(\w+)""").findAll(index).map { it.groupValues[1] }.toSet()

    @Test
    fun everyRowEachScreenRendersIsSearchable() {
        for ((route, file, _) in screens) {
            val missing = renderedTitles(file) - indexedNames
            assertTrue("$route has unsearchable rows: $missing", missing.isEmpty())
        }
    }

    @Test
    fun theIndexNamesNothingTheScreensDoNotRender() {
        val rendered = screens.flatMap { renderedTitles(it.second) }.toSet()
        val phantom = indexedNames - rendered
        assertTrue("index names options that do not exist: $phantom", phantom.isEmpty())
    }

    @Test
    fun everyOptionResolvesToACardAndARoute() {
        for (opt in SettingsSearch.LEGACY_OPTIONS) {
            assertTrue(
                "option ${opt.rowRes} has no card",
                opt.card >= 0 && SettingsSearch.cardOf(opt.rowRes) == opt.card
            )
            assertEquals(opt.route, SettingsSearch.routeOf(opt.rowRes))
        }
    }

    @Test
    fun cardIndexesAreUniqueAcrossTheTree() {
        // One offset map serves every screen, so a card index has to identify a
        // card on its own. Per-screen numbering would make card 3 ambiguous.
        val perCard = SettingsSearch.LEGACY_OPTIONS.groupBy { it.card }
        for ((card, opts) in perCard) {
            assertEquals("card $card spans two routes", 1, opts.map { it.route }.distinct().size)
        }
    }

    @Test
    fun noOptionIsListedTwice() {
        val all = SettingsSearch.LEGACY_ROWS
        assertEquals(all.size, all.distinct().size)
    }

    @Test
    fun anOptionOutsideTheIndexHasNoCard() {
        assertEquals(-1, SettingsSearch.cardOf(-1))
        assertEquals(SettingsSearch.Route.SETTINGS, SettingsSearch.routeOf(-1))
    }

    @Test
    fun everyRouteHasOptions() {
        val routes = SettingsSearch.LEGACY_OPTIONS.map { it.route }.distinct()
        assertEquals(3, routes.size)
        routes.forEach { assertTrue("route $it has no options", it in screens.map { s -> s.first }) }
    }
}