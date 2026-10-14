package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adding people from the picker must respect where it was opened from:
 *
 *  - a group gets the people appended in place ("Alex + Bank" becomes a group of
 *    three), instead of a second group being created next to it;
 *  - a 1:1 starts a new group thread, leaving the private history alone.
 *
 * The group's automatic name must grow with its members, but a name the user
 * typed stays put.
 */
class GroupMembershipWiringTest {
    private val main: String by lazy { locate("MainActivity.kt") }
    private val repo: String by lazy { locate("data/Repository.kt") }

    @Test
    fun addingToAGroupAppendsInsteadOfCreatingANewOne() {
        val body = main.substringAfter("fun finishAddPeople(").substringBefore("\n    }")
        assertTrue(
            "a group must branch on isGroup",
            body.contains("if (repo.isGroup(conversationId))"),
        )
        assertTrue(
            "a group must add the people to itself",
            body.contains("repo.addParticipants(conversationId, addresses)"),
        )
        assertTrue(
            "a group must keep its own id",
            Regex("""addParticipants\(conversationId, addresses\)[\s\S]{0,120}?conversationId\s*\n""")
                .containsMatchIn(body),
        )
        assertTrue(
            "a 1:1 must still start a new group thread",
            body.contains("repo.createGroupFrom(conversationId, addresses)"),
        )
    }

    @Test
    fun addingMembersRefreshesTheAutomaticName() {
        val body = repo.substringAfter("suspend fun addParticipants(").substringBefore("\n    }")
        assertTrue(
            "adding people must refresh the group name",
            body.contains("ensureGroupTitle(conversationId)"),
        )
    }

    @Test
    fun aTypedNameIsNotOverwrittenButAnAutomaticOneIs() {
        // ensureGroupTitle reads the custom flag and bails when it is set.
        val title = repo.substringAfter("private fun ensureGroupTitle(").substringBefore("\n    }")
        assertTrue(
            "the automatic name must stop for a user-set name",
            title.contains("group_title_custom"),
        )
        assertTrue(
            "a set flag must skip the recompute",
            Regex("""group_title_custom[\s\S]{0,200}?if \(custom\) return""").containsMatchIn(title),
        )
        assertTrue(
            "renaming must record that the name is the user's",
            repo.contains("""put("group_title_custom", if (clean.isEmpty()) 0 else 1)"""),
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
