package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contact-details page: one design, whatever the rest of the app renders.
 *
 * It used to exist twice, and the two drifted. `use_new_ui` picked between them,
 * so anything fixed in one stayed broken in the other. The screen they were
 * both trying to be now lives in `ui/ContactDetailsScreen.kt` and there is no
 * second copy to fall behind.
 *
 * The behaviour pinned here is the part of the merge people notice:
 *  - the group name is edited in place, with the pencil below it
 *  - a group shows no per-person actions, no number, no block row
 *  - a saved contact offers Info instead of creating a duplicate
 *  - member rows are listed for a group and only for a group
 *
 * All of it is asserted at the source level, because these live inside the
 * Scaffold's composition and none of it is reachable from a JVM test.
 */
class ContactDetailsWiringTest {
    private val screen: String by lazy { locate("ui/ContactDetailsScreen.kt") }
    private val main: String by lazy { locate("MainActivity.kt") }
    private val helpers: String by lazy { locate("ui/ContactDetails.kt") }

    @Test
    fun addPeopleOpensTheInAppPickerNotTheContactCreator() {
        // The row runs from the participant-count label to the member list below
        // it. Scoping matters: the screen's own "Contact" action button
        // legitimately launches the insert intent, so a whole-file check would
        // trip over it.
        val row = screen
            .substringAfter("R.string.contact_one_person")
            .substringBefore("recipients.forEachIndexed")
        assertTrue(
            "the Add people row must call onAddPeople()",
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
    fun theScreenAcceptsAnAddPeopleCallback() {
        assertTrue(
            "ContactDetailsScreen must take onAddPeople",
            Regex("fun ContactDetailsScreen\\([\\s\\S]*?onAddPeople: \\(\\) -> Unit").containsMatchIn(screen),
        )
        assertTrue(
            "MainActivity must pass onAddPeople to the details screen",
            Regex("ContactDetailsScreen\\([\\s\\S]*?onAddPeople = \\{ navRoute = \"add-people\" \\}")
                .containsMatchIn(main),
        )
    }

    @Test
    fun thereIsOnlyOneContactDetailsScreen() {
        // The merge is only done if the second copy is gone. Left in place it
        // would go on drifting, and the flag would go on choosing between them.
        val uiDir = locateDir("ui")
        val screens = uiDir.walkTopDown()
            .filter { it.name.endsWith("ContactDetailsScreen.kt") }
            .toList()
        assertEquals(
            "a second contact-details screen reappeared",
            1,
            screens.size,
        )
        assertFalse(
            "the use_new_ui branch must not decide between two contact pages",
            main.contains("legacy.ContactDetailsScreen"),
        )
    }

    @Test
    fun memberRowsAreListedForGroupsOnly() {
        // A 1:1's single member is already named and numbered in the header, so
        // listing them again is pure duplication. A group must list everyone —
        // that list is also the only UI for removeParticipant.
        assertTrue(
            "the member rows must be gated on isGroup",
            Regex("""if \(isGroup\) \{[\s\S]{0,300}?recipients\.forEachIndexed""")
                .containsMatchIn(screen),
        )
    }

    @Test
    fun theGroupNameIsEditedInPlaceNotInADialog() {
        // An outlined box over the header reads as a separate window; the field
        // replaces the title itself.
        assertTrue(
            "the screen must edit the name inline",
            screen.contains("if (renaming) {"),
        )
        assertFalse(
            "no rename dialog should remain",
            screen.contains("title = { Text(stringResource(R.string.contact_group_name)) }"),
        )
        assertTrue(
            "the inline field must not draw an underline",
            Regex("""TextFieldDefaults\.colors\([\s\S]{0,800}?focusedIndicatorColor = Color\.Transparent""")
                .containsMatchIn(screen),
        )
    }

    @Test
    fun aSavedContactOffersInfoRatherThanCreateNewContact() {
        // For someone already in Contacts, "Contact" is a button that can only
        // ever create a duplicate.
        val actions = screen.substringAfter("Icons.Rounded.Call")
            .substringBefore("Icons.Rounded.PersonAdd")
            .ifEmpty { screen.substringAfter("Icons.Rounded.Call").take(1200) }
        assertTrue(
            "the screen must look up the saved contact",
            screen.contains("ContactLookup.find(context, address)"),
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
        assertTrue(
            "the saved branch must be gated on the lookup having finished",
            screen.contains("contactsLoaded && savedContact != null"),
        )
    }

    @Test
    fun aGroupHasNoPerPersonActions() {
        // A group is a thread, not a person: there is no single number to call,
        // view in Contacts, or block. Those all belong to a 1:1.
        val actions = screen
            .substringAfter("Spacer(Modifier.height(16.dp))")
            .substringBefore("contact_notifications")
        assertTrue(
            "the Call/Contact row must be gated on !isGroup",
            Regex("""if \(!isGroup\) \{\s*\n\s*Row\(""").containsMatchIn(actions),
        )
        val blockRow = screen.substringAfter("contact_notifications")
        assertTrue(
            "Block number must be gated on !isGroup",
            Regex("""if \(!isGroup\) \{[\s\S]{0,200}?contact_block_report""").containsMatchIn(blockRow),
        )
    }

    @Test
    fun theConversationListTitlesGroupsFromTheirMembers() {
        // Without this a group shows as whoever it started with, so the home
        // list said "Sarah" for a Sarah + Dad thread -- and, in the redesigned
        // list, so did the label a screen reader announced.
        //
        // Both lists now call the one helper. It is asserted here rather than in
        // each list because the rule being in three places is what let the
        // redesigned list lose it in the first place.
        assertTrue(
            "the rule must live in ContactDetails",
            Regex("""fun listLabel\([\s\S]{0,200}?groupTitle\.isNotBlank\(\)\) groupTitle""")
                .containsMatchIn(helpers),
        )
        listOf("ui/ConversationsScreen.kt", "ui/legacy/LegacyConversationsScreen.kt").forEach { path ->
            val src = locate(path)
            assertTrue("$path must use the shared label", src.contains("ContactDetails.listLabel("))
            assertTrue(
                "$path must not decide the title for itself again",
                !src.contains("convo.groupTitle.isNotBlank()"),
            )
        }
    }

    @Test
    fun thePencilSitsBelowTheNameNotBesideIt() {
        // Beside a long group name the icon reads as part of the name.
        assertTrue(
            "the title and pencil must share a Column",
            Regex("""text = groupTitle[\s\S]{0,1400}?Icons\.Rounded\.Edit""")
                .containsMatchIn(screen),
        )
        assertTrue(
            "the pencil must not sit in the name's Row",
            !Regex("""Row\([\s\S]{0,300}?text = groupTitle""").containsMatchIn(screen),
        )
    }

    @Test
    fun renamingStartsWithTheCaretAtTheEnd() {
        // The name is a generated default the user normally replaces wholesale.
        // Starting at offset 0 means typing inserts mid-name and every edit
        // begins with a manual walk to the end.
        assertTrue(
            "the draft must be a TextFieldValue so selection is controllable",
            screen.contains("mutableStateOf(TextFieldValue(\"\"))"),
        )
        assertTrue(
            "opening the editor must put the caret at the end",
            Regex("""TextFieldValue\(\s*\n?\s*groupTitle,\s*\n?\s*TextRange\(groupTitle\.length\)""")
                .containsMatchIn(screen),
        )
    }

    @Test
    fun aGroupShowsNoNumberUnderItsName() {
        // One member's number under a group name reads as though the whole
        // thread belongs to them.
        assertTrue(
            "the subtitle must be gated on !isGroup",
            Regex("""if \(!isGroup\) \{\s*\n\s*ContactDetails\.subtitle""").containsMatchIn(screen),
        )
    }

    @Test
    fun addingPeopleStaysOnTheDetailsScreen() {
        // It is a setup screen, not somewhere to read or write: bouncing the
        // user into the chat made them think the group had been created and
        // already had something in it. The screen then shows the new group,
        // where its name can be edited.
        assertTrue(
            "completing add-people must start a new group and stay on details",
            Regex("""vm\.createGroup\([\s\S]{0,300}?navRoute = "details"\s*\n""")
                .containsMatchIn(main),
        )
        assertFalse(
            "no jump into the chat after adding people",
            main.contains("""onDone = {
                                                    chatId = detailsId"""),
        )
    }

    @Test
    fun theParticipantCountRowSurvives() {
        assertTrue(
            "the 'N other person' count must stay",
            screen.contains("contact_one_person"),
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

    private fun locateDir(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/com/anindra/messages/$relative")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("could not locate $relative")
    }
}
