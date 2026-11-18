package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rules page must not claim a tab is empty before it has read the table.
 *
 * Seeding the screen with an empty list made every tab say "no rules yet" for
 * as long as the first query took — long enough to look like a rule the user had
 * just written had been thrown away, and it came back a moment later.
 *
 * Two things hold that line: the rules flow starts as null (not as an empty
 * list), and the page draws no sections until that first answer lands.
 */
class RulesFirstReadTest {

    private val app = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app") }
        .firstOrNull { File(it, "src").isDirectory }
        ?: error("app/src not found")

    private val main = app.resolve("src/main/java/com/anindra/messages/MainActivity.kt").readText()
    private val screen =
        app.resolve("src/main/java/com/anindra/messages/ui/InboxSettingsScreen.kt").readText()

    @Test
    fun theRulesFlowStartsEmptyOnlyAfterItsFirstRead() {
        assertTrue(
            "categoryRules no longer waits for its first read (null seed)",
            Regex("""stateIn\(\s*scope,\s*SharingStarted\.Eagerly,\s*null\s*\)""").containsMatchIn(main)
        )
    }

    @Test
    fun thePageDoesNotSeedItselfWithAnEmptyList() {
        assertFalse(
            "the rules page still seeds empty, so every tab flashes \"no rules yet\"",
            screen.contains("vm.categoryRules.collectAsState(initial = emptyList())")
        )
    }

    @Test
    fun noSectionIsDrawnBeforeTheRulesArrive() {
        assertTrue(
            "the sections are drawn from a possibly-null answer",
            screen.contains("rules?.let { InboxCategoryRules.group(it) }")
        )
        assertFalse(
            "the sections fall back to an empty grouping before the read",
            screen.contains("InboxCategoryRules.group(rules)")
        )
    }
}
