package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #478: an expanded window shows the conversation list and the chat side by
 * side instead of stretching the phone UI. The split has to be a layout of the
 * one navigation stack, not a second one that drifts from it.
 */
class TwoPaneLayoutTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val activity = File(main, "java/com/anindra/messages/MainActivity.kt").readText()

    @Test
    fun theWidthDecidesAndOnlyAnExpandedWindowSplits() {
        assertTrue(
            "the window size class is what decides, not a hardcoded width",
            activity.contains("calculateWindowSizeClass(this@MainActivity)")
        )
        assertTrue(
            "a phone keeps one pane; only an expanded window splits",
            activity.contains("windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded")
        )
    }

    @Test
    fun bothPanesAreTheSameComposablesAsTheSinglePane() {
        assertTrue(
            "the list is one lambda, used by both layouts",
            activity.contains("val conversationList: @Composable () -> Unit = {")
        )
        assertTrue(
            "the route stack is one lambda taking the pane's size",
            activity.contains("val routePane: @Composable (Modifier) -> Unit = { paneModifier ->")
        )
        val listUses = Regex("""conversationList\(\)""").findAll(activity).count()
        val paneUses = Regex("""routePane\(Modifier\.fillMaxSize\(\)\)""").findAll(activity).count()
        assertTrue("both layouts render the list ($listUses uses)", listUses >= 2)
        assertTrue("both layouts render the route stack ($paneUses uses)", paneUses >= 2)
    }

    @Test
    fun theSplitIsALayoutNotASecondNavigationStack() {
        assertTrue(
            "one AnimatedContent still owns every route",
            Regex("""AnimatedContent\(\s*targetState = navRoute,""").containsMatchIn(activity)
        )
        assertTrue(
            "and there is exactly one of it",
            Regex("""AnimatedContent\(""").findAll(activity).count() == 1
        )
        assertTrue(
            "the list keeps its own column and the route stack takes the rest",
            activity.contains("Modifier.weight(listWeight)") &&
                activity.contains("Modifier.weight(1f - listWeight)")
        )
    }

    @Test
    fun anEmptyRightPaneSaysWhatToDo() {
        assertTrue(
            "with no chat open the split window is not a blank half screen",
            Regex("""if \(twoPaneLayout\) \{[\s\S]{0,500}?R\.string\.two_pane_empty""")
                .containsMatchIn(activity)
        )
    }
}
