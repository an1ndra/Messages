package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The legacy contact-details screen is the default one (`use_new_ui=false`),
 * so its behaviour is what most users actually get.
 *
 * Two faults lived there and nowhere else, because the modern screen had them
 * right and the two were written separately:
 *  - "Add people" opened the system *create new contact* intent, a completely
 *    different thing, because the legacy screen was never given the callback.
 *  - The participant row repeated the name and number already shown above it.
 *
 * Both are pinned at the source level: neither the intent that used to be
 * launched nor the composable that used to be dropped is reachable from a JVM
 * test, and the modern screen passing would say nothing about the legacy one.
 */
class LegacyContactDetailsWiringTest {
    private val legacy: String by lazy { locate("ui/legacy/LegacyContactDetailsScreen.kt") }
    private val modern: String by lazy { locate("ui/ContactDetailsScreen.kt") }
    private val main: String by lazy { locate("MainActivity.kt") }

    @Test
    fun addPeopleOpensTheInAppPickerNotTheContactCreator() {
        // The row runs from the participant-count label to the commented-out
        // block below it. Scoping matters: the screen's own "Contact" action
        // button legitimately launches the insert intent, so a whole-file check
        // would trip over it.
        val row = legacy
            .substringAfter("R.string.contact_one_person")
            .substringBefore("// Commented out at the user's request")
        assertTrue(
            "the legacy Add people row must call onAddPeople()",
            row.contains("Modifier.clickable { onAddPeople() }"),
        )
        assertFalse(
            "Add people must not launch the system create-new-contact intent",
            row.contains("ContactsContract.Intents.Insert"),
        )
        assertTrue(
            "the row must still render the Add people label",
            row.contains("contact_add_people"),
        )
    }

    @Test
    fun theLegacyScreenAcceptsAnAddPeopleCallback() {
        assertTrue(
            "ContactDetailsScreen (legacy) must take onAddPeople",
            Regex("fun ContactDetailsScreen\\([\\s\\S]*?onAddPeople: \\(\\) -> Unit").containsMatchIn(legacy),
        )
        assertTrue(
            "MainActivity must pass onAddPeople to the legacy details screen",
            Regex("legacy\\.ContactDetailsScreen\\([\\s\\S]*?onAddPeople = \\{ navRoute = \"add-people\" \\}")
                .containsMatchIn(main),
        )
    }

    @Test
    fun theRedundantParticipantRowIsCommentedOutOnBothScreens() {
        // Commented rather than deleted, so the participant rows — and with them
        // the only UI for removeParticipant — stay one uncomment away. What must
        // not survive is a *live* copy of that row.
        assertFalse(
            "the legacy screen must not render a live participant row",
            legacy.lineSequence().any {
                it.isNotBlank() && !it.trimStart().startsWith("//") &&
                    it.contains("PersonAvatar(address, size = 40.dp)")
            },
        )
        assertFalse(
            "the modern screen must not render live recipient rows",
            Regex("^\\s*recipients\\.forEachIndexed", RegexOption.MULTILINE).containsMatchIn(modern),
        )
        assertTrue(
            "the legacy participant row should still be present, commented",
            legacy.contains("//     PersonAvatar(address, size = 40.dp)"),
        )
        assertTrue(
            "the modern recipient rows should still be present, commented",
            modern.contains("// recipients.forEachIndexed"),
        )
    }

    @Test
    fun aSavedContactOffersInfoRatherThanCreateNewContact() {
        // The modern screen has had this since it was written; the legacy one
        // always offered "Add", which for someone already in Contacts is a
        // button that can only ever create a duplicate.
        val actions = legacy.substringAfter("Icons.Rounded.Call")
            .substringBefore("Icons.Rounded.PersonAdd")
            .ifEmpty { legacy.substringAfter("Icons.Rounded.Call").take(1200) }
        assertTrue(
            "the legacy screen must look up the saved contact",
            legacy.contains("ContactLookup.find(context, address)"),
        )
        assertTrue(
            "a saved contact must get the Info action",
            actions.contains("R.string.action_info"),
        )
        assertTrue(
            "Info must open the saved contact",
            actions.contains("savedContact!!.viewUri()"),
        )
        assertTrue(
            "an unsaved contact must still get the Contact action",
            actions.contains("R.string.action_contact"),
        )
        assertFalse(
            "the saved branch must not be gated on contactsLoaded being false only",
            !legacy.contains("contactsLoaded && savedContact != null"),
        )
    }

    @Test
    fun theParticipantCountRowSurvives() {
        assertTrue(
            "the 'N other person' count must stay",
            legacy.contains("contact_one_person"),
        )
        assertTrue(
            "the 'N other person' count must stay (modern)",
            modern.contains("contact_one_person"),
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