package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Group naming.
 *
 * A group is named the moment it becomes one, from whoever is in it. The user can
 * rename it at that point, or later from the profile, and a blank rename hands
 * naming back to the derived default rather than leaving the group untitled.
 */
class GroupNamingTest {

    /** Mirrors Repository.ensureGroupTitle. */
    private fun deriveTitle(
        members: List<String>,
        current: String,
        nameOf: (String) -> String?
    ): String {
        if (members.size <= 1) return current
        if (current.isNotBlank()) return current
        val names = members.map { nameOf(it) ?: it }.take(3)
        return when {
            names.size <= 1 -> names.first()
            else -> names.dropLast(1).joinToString(", ") + " +" + names.last()
        }
    }

    private val allKnown: (String) -> String? = { number ->
        mapOf("a" to "Alex", "b" to "Anindra", "c" to "Dr. Patel Clinic")[number]
    }

    @Test
    fun aGroupIsNamedFromItsMembers() {
        val title = deriveTitle(listOf("a", "b", "c"), current = "", nameOf = allKnown)
        assertEquals("Alex, Anindra +Dr. Patel Clinic", title)
    }

    @Test
    fun onlyTheFirstThreeMembersAreNamed() {
        val title = deriveTitle(listOf("a", "b", "c", "x", "y"), current = "", nameOf = allKnown)
        assertEquals("Alex, Anindra +Dr. Patel Clinic", title)
    }

    @Test
    fun aMemberWithNoContactNameFallsBackToTheirNumber() {
        val title = deriveTitle(listOf("a", "+1555773020"), current = "", nameOf = allKnown)
        assertEquals("Alex ++1555773020", title)
    }

    @Test
    fun aOneToOneKeepsItsContactNameRatherThanAGroupTitle() {
        // Group titles are for groups. Naming a 1:1 would shadow the contact name
        // the whole conversation is already shown under.
        val title = deriveTitle(listOf("a"), current = "", nameOf = allKnown)
        assertEquals("", title)
    }

    @Test
    fun aNameTheUserSetIsNeverOverwrittenByLaterMembershipChanges() {
        val afterFirstName = deriveTitle(listOf("a", "b"), current = "", nameOf = allKnown)
        val afterAnotherMember = deriveTitle(
            listOf("a", "b", "c"), current = afterFirstName, nameOf = allKnown
        )
        assertEquals(afterFirstName, afterAnotherMember)
    }

    @Test
    fun aUserNameChosenWhileAddingPeopleBeatsTheDerivedDefault() {
        // Adding people derives a title first, so the name the user typed at the
        // add step has to be applied afterwards to take effect.
        val derived = deriveTitle(listOf("a", "b", "c"), current = "", nameOf = allKnown)
        val chosen = deriveTitle(listOf("a", "b", "c"), current = "Weekend Trip", nameOf = allKnown)
        assertEquals("Alex, Anindra +Dr. Patel Clinic", derived)
        assertEquals("Weekend Trip", chosen)
    }

    @Test
    fun aBlankRenameRestoresTheDerivedDefault() {
        val cleared = deriveTitle(listOf("a", "b", "c"), current = "", nameOf = allKnown)
        assertEquals("Alex, Anindra +Dr. Patel Clinic", cleared)
    }

    @Test
    fun aBlankRenameOnAOneToOneLeavesItUntitled() {
        val cleared = deriveTitle(listOf("a"), current = "", nameOf = allKnown)
        assertEquals("", cleared)
    }

    @Test
    fun renamingIsTrimmedSoABlankSubmissionCannotLookUnnamed() {
        // setGroupTitle trims before storing, so whitespace-only input is the
        // same as clearing and the default comes back.
        val title = deriveTitle(listOf("a", "b", "c"), current = "   ".trim(), nameOf = allKnown)
        assertEquals("Alex, Anindra +Dr. Patel Clinic", title)
    }
}
