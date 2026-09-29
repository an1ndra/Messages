package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings screens are swapped through an `AnimatedContent`, so the outgoing
 * composable is disposed and rebuilt on the way back. A screen that creates its
 * scroll state inline therefore comes back at 0. Each one must take the state
 * as a parameter, and MainActivity must hoist it above the animation.
 */
class SettingsScrollStateTest {

    private val main = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .firstOrNull { it.isDirectory }
        ?: error("app/src/main not found")

    private fun source(name: String) =
        File(main, "java/com/anindra/messages/ui/$name.kt").readText()

    private val mainActivity = File(main, "java/com/anindra/messages/MainActivity.kt").readText()

    @Test
    fun advancedTakesTheScrollStateInsteadOfCreatingItInline() {
        val src = source("AdvancedSettingsScreen")
        assertTrue("scrollState param", "scrollState: ScrollState" in src)
        assertTrue("hoisted state is used", "verticalScroll(scrollState)" in src)
        assertFalse(
            "AdvancedSettingsScreen still creates its own scroll state",
            "verticalScroll(rememberScrollState())" in src
        )
    }

    @Test
    fun trashTakesAListStateForEachTab() {
        val src = source("TrashScreen")
        assertEquals(2, Regex("ListState: LazyListState").findAll(src).count())
        assertEquals(2, Regex("state = \\w+ListState").findAll(src).count())
    }

    @Test
    fun spamBlockedTakesAListStateForEachTab() {
        val src = source("SpamBlockedScreen")
        assertEquals(2, Regex("ListState: LazyListState").findAll(src).count())
        assertEquals(2, Regex("state = listState").findAll(src).count())
    }

    @Test
    fun mainActivityHoistsEverySettingsScrollStateAboveTheNavAnimation() {
        val hoisted = listOf(
            "settingsScroll", "advancedScroll", "accessibilityScroll",
            "contactDetailsScroll", "trashConversationList",
            "trashMessageList", "spamConversationList", "spamMessageList"
        )
        for (name in hoisted) {
            val declared = Regex("(val|var) $name = remember(ScrollState|LazyListState)\\(")
            assertTrue("$name is not hoisted", declared.containsMatchIn(mainActivity))
        }
    }

    @Test
    fun everyHoistedStateIsWiredIntoItsScreen() {
        val wired = listOf(
            "scrollState = settingsScroll", "scrollState = advancedScroll",
            "scrollState = accessibilityScroll", "scrollState = contactDetailsScroll",
            "conversationListState = trashConversationList",
            "messageListState = trashMessageList",
            "conversationListState = spamConversationList",
            "messageListState = spamMessageList"
        )
        for (call in wired) {
            assertTrue("$call is missing", call in mainActivity)
        }
    }

    /**
     * The durable guard, driven off MainActivity's own route map so a route
     * screen added later is covered without touching this test. Any screen
     * rendered inside the `AnimatedContent` is disposed and rebuilt on the way
     * back, so it must never create its own scroll state — that is the whole of
     * issue #265, and it recurred in two more screens before this was found.
     */
    @Test
    fun noRouteScreenCreatesItsOwnScrollState() {
        val routeScreens = Regex("\"[a-z]+\"\\s*->\\s*([A-Z][A-Za-z]*)\\(")
            .findAll(mainActivity)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("route map not found in MainActivity", routeScreens.isNotEmpty())

        // ChatScreen intentionally re-opens at the newest message (#179), and
        // ConversationsScreen is rendered outside the AnimatedContent so it is
        // never disposed. Everything else must take a hoisted state.
        val exempt = setOf("ChatScreen", "ConversationsScreen")
        val problems = mutableListOf<String>()
        for (screen in routeScreens - exempt) {
            val file = File(main, "java/com/anindra/messages/ui/$screen.kt")
            if (!file.isFile) continue
            val body = composableBody(file.readText(), screen)
            for (call in listOf("verticalScroll(rememberScrollState())", "rememberLazyListState()")) {
                if (call in body) problems += "$screen: $call"
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    /** The composable's body with its parameter list removed, so a legitimate
     *  default value like `scrollState: ScrollState = rememberScrollState()`
     *  is not mistaken for the screen creating its own state. */
    private fun composableBody(src: String, name: String): String {
        val start = src.indexOf("fun $name(")
        if (start < 0) return ""
        var depth = 0
        var i = start + "fun $name(".length - 1
        do {
            when (src[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            i++
        } while (depth > 0 && i < src.length)
        return src.substring(i)
    }

    @Test
    fun dialogsAreExemptBecauseTheyAreNotRouteScreens() {
        // Guard the exemption list above: a dialog legitimately creates its own
        // scroll state, so it must not be hoisted and must not be in the map.
        val routeScreens = Regex("\"[a-z]+\"\\s*->\\s*([A-Z][A-Za-z]*)\\(")
            .findAll(mainActivity).map { it.groupValues[1] }.toSet()
        assertFalse(routeScreens.any { it.endsWith("Dialog") })
    }

    @Test
    fun hoistedStatesAreDeclaredBeforeTheAnimatedContentThatSwapsScreens() {
        val lastDecl = mainActivity.lastIndexOf("val spamMessageList = rememberLazyListState()")
        val animatedContent = mainActivity.indexOf("AnimatedContent(")
        assertTrue("no hoisted spamMessageList found", lastDecl > 0)
        assertTrue("hoisted after AnimatedContent", animatedContent > lastDecl)
    }
}
