package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #405: the contact list behind New chat and the Forward picker used to be the
 * snapshot taken when the ViewModel was built, and that ViewModel lives as long
 * as the activity — the chat is a route inside it, not a screen of its own. A
 * contact deleted in the Contacts app therefore stayed offered as a forward
 * target ("cannot be deleted"), and one added never appeared, until the process
 * was killed.
 *
 * The list has to be re-read when Contacts reports a change.
 */
class ContactsReloadWiringTest {

    private val main: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
    }

    private val source: String by lazy {
        File(main, "java/com/anindra/messages/MainActivity.kt").readText()
    }

    @Test
    fun theContactListIsObservedForChanges() {
        assertTrue(
            "AppViewModel must register a ContentObserver on Contacts",
            Regex("registerContentObserver\\(\\s*android\\.provider\\.ContactsContract\\.Contacts\\.CONTENT_URI")
                .containsMatchIn(source)
        )
        assertTrue(
            "the Data table must be observed too: a phone number edit lands there",
            Regex("registerContentObserver\\(\\s*android\\.provider\\.ContactsContract\\.Data\\.CONTENT_URI")
                .containsMatchIn(source)
        )
    }

    @Test
    fun aChangeRebuildsTheListTheScreensCollect() {
        assertTrue(
            "a change must re-read the roster into vm.contacts, not just the database names",
            Regex("readContacts\\(ctx\\)").containsMatchIn(source) &&
                Regex("contacts\\.value = roster").containsMatchIn(source)
        )
        assertTrue(
            "the roster read must go through ContactRoster.merge",
            Regex("ContactRoster\\.merge\\(rows\\)").containsMatchIn(source)
        )
    }

    @Test
    fun theObserverIsUnregisteredWithTheViewModel() {
        val cleared = source.substringAfter("override fun onCleared()").substringBefore("super.onCleared()")
        assertTrue(
            "onCleared must unregister the Contacts observer",
            cleared.contains("unregisterContentObserver")
        )
        assertTrue(
            "onCleared must drop the pending debounced reload",
            cleared.contains("ui.removeCallbacks(contactsReload)")
        )
    }
}
