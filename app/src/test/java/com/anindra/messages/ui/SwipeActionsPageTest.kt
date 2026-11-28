package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Inbox settings used to carry two swipe rows and open a picker on the spot.
 * They now open one page that owns both directions and their picker, so the
 * Inbox list stays short and the two choices still write the same store keys.
 */
class SwipeActionsPageTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val inbox: String by lazy {
        File(main, "java/com/anindra/messages/ui/InboxSettingsScreen.kt").readText()
    }

    private val page: String by lazy {
        File(main, "java/com/anindra/messages/ui/SwipeActionsScreen.kt").readText()
    }

    @Test
    fun inboxHasOneRowThatOpensThePage() {
        assertTrue(
            "Inbox settings has no Swipe actions row",
            Regex("""SettingsRow\([\s\S]*?settings_swipe_actions_title[\s\S]*?\n\s*\)""")
                .containsMatchIn(inbox)
        )
        assertTrue("the row does not open the page", inbox.contains("showSwipeActions = true"))
        assertTrue("the page is not rendered", inbox.contains("SwipeActionsScreen("))
        assertFalse(
            "the old inline left row is still in Inbox settings",
            Regex("""title = stringResource\(R\.string\.swipe_left_title\)""").containsMatchIn(inbox)
        )
        assertFalse(
            "the old inline right row is still in Inbox settings",
            Regex("""title = stringResource\(R\.string\.swipe_right_title\)""").containsMatchIn(inbox)
        )
    }

    @Test
    fun thePageOwnsBothDirectionsAndTheirPicker() {
        assertTrue("the page has no left row", page.contains("R.string.swipe_left_title"))
        assertTrue("the page has no right row", page.contains("R.string.swipe_right_title"))
        assertTrue("the picker is not on the page", page.contains("SwipeActionDialog("))
        assertTrue("the left choice is not saved", page.contains("onLeftChange(picked)"))
        assertTrue("the right choice is not saved", page.contains("onRightChange(picked)"))
        assertTrue(
            "the page does not use settings rows",
            page.contains("SettingsGroup {") && page.contains("RowPosition.FIRST") &&
                page.contains("RowPosition.LAST")
        )
    }
}
