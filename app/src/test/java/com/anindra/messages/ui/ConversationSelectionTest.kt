package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSelectionTest {

    @Test
    fun togglingAddsThenRemovesOneConversation() {
        var selected = ConversationSelection.toggle(emptySet(), 7L)
        assertEquals(setOf(7L), selected)
        selected = ConversationSelection.toggle(selected, 9L)
        assertEquals(setOf(7L, 9L), selected)
        selected = ConversationSelection.toggle(selected, 7L)
        assertEquals(setOf(9L), selected)
    }

    @Test
    fun selectionIsActiveOnlyWhileSomethingIsPicked() {
        assertFalse(ConversationSelection.isActive(emptySet()))
        assertTrue(ConversationSelection.isActive(setOf(1L)))
    }

    @Test
    fun selectAllTakesOnlyTheVisibleRows() {
        assertEquals(
            setOf(1L, 2L),
            ConversationSelection.selectAll(listOf(1L, 2L))
        )
    }

    @Test
    fun pruningDropsRowsThatLeftTheScreen() {
        // A conversation deleted from the sheet, or filtered out by a search,
        // must not keep counting in the toolbar.
        assertEquals(
            setOf(2L),
            ConversationSelection.prune(setOf(1L, 2L), listOf(2L, 3L))
        )
    }

    @Test
    fun trashTargetsFollowTheVisibleOrder() {
        assertEquals(
            listOf(3L, 1L),
            ConversationSelection.trashTargets(setOf(1L, 3L), listOf(3L, 2L, 1L))
        )
        assertEquals(
            emptyList<Long>(),
            ConversationSelection.trashTargets(setOf(5L), listOf(1L, 2L))
        )
    }
}
