package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Add people" in a chat's overflow menu was a no-op stub, so the menu item did
 * nothing at all (#327). The picker already existed and was reachable from
 * contact details; this pins that the chat wires the same picker and comes back
 * to where it was opened from.
 */
class AddPeopleFromChatWiringTest {
    private val chat: String by lazy { locate("ui/ChatScreen.kt") }
    private val main: String by lazy { locate("MainActivity.kt") }

    @Test
    fun chatScreenAcceptsAnAddPeopleCallback() {
        assertTrue(
            "ChatScreen must take onAddPeople",
            Regex("fun ChatScreen\\([\\s\\S]*?onAddPeople: \\(\\) -> Unit").containsMatchIn(chat),
        )
    }

    @Test
    fun theOverflowMenuItemCallsTheCallback() {
        assertTrue(
            "the menu must forward to onAddPeople",
            chat.contains("onAddPeople = onAddPeople,"),
        )
        assertFalse(
            "the add-people menu item must not stay a stub",
            chat.contains("no-op: MMS group chat, stub"),
        )
    }

    @Test
    fun mainActivityOpensThePickerFromTheChat() {
        val call = main.substringAfter("else -> ChatScreen(")
        assertTrue(
            "the chat must open the add-people picker for its own conversation",
            Regex("onAddPeople = \\{[\\s\\S]{0,200}?detailsId = chatId[\\s\\S]{0,200}?navRoute = \"add-people\"")
                .containsMatchIn(call),
        )
    }

    @Test
    fun thePickerReturnsToWhereItWasOpenedFrom() {
        assertTrue(
            "the picker must use the stored return route",
            main.contains("onBack = { navRoute = addPeopleReturn }"),
        )
        assertTrue(
            "opening from a chat must aim back at the chat",
            Regex("onAddPeople = \\{[\\s\\S]{0,120}?addPeopleReturn = \"chat\"").containsMatchIn(main),
        )
        assertTrue(
            "opening from details must aim back at details",
            main.contains("onAddPeople = { addPeopleReturn = \"details\"; navRoute = \"add-people\" }"),
        )
    }

    private fun locate(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/com/anindra/messages/$relative")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate $relative")
    }
}
