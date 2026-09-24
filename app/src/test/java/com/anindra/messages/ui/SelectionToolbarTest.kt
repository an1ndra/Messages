package com.anindra.messages.ui

import com.anindra.messages.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionToolbarTest {

    @Test
    fun singleMessageActionsOnlyForOneSelected() {
        assertTrue(SelectionToolbar.showSingleMessageActions(1))
        assertFalse(SelectionToolbar.showSingleMessageActions(0))
        assertFalse(SelectionToolbar.showSingleMessageActions(2))
        assertFalse(SelectionToolbar.showSingleMessageActions(5))
    }

    @Test
    fun copyIsOfferedForEverySelection() {
        assertFalse(SelectionToolbar.showCopy(0))
        assertTrue(SelectionToolbar.showCopy(1))
        assertTrue(SelectionToolbar.showCopy(2))
        assertTrue(SelectionToolbar.showCopy(5))
    }

    @Test
    fun forwardAndTrashAreOfferedForEverySelection() {
        assertFalse(SelectionToolbar.showForward(0))
        assertTrue(SelectionToolbar.showForward(1))
        assertTrue(SelectionToolbar.showForward(3))

        assertFalse(SelectionToolbar.showTrash(0))
        assertTrue(SelectionToolbar.showTrash(1))
        assertTrue(SelectionToolbar.showTrash(3))
    }

    @Test
    fun deleteIsAlwaysAvailableWhenSomethingIsSelected() {
        assertFalse(SelectionToolbar.showDelete(0))
        assertTrue(SelectionToolbar.showDelete(1))
        assertTrue(SelectionToolbar.showDelete(3))
    }

    @Test
    fun selectionIsActiveOnlyWithASelection() {
        assertFalse(SelectionToolbar.isSelectionActive(0))
        assertTrue(SelectionToolbar.isSelectionActive(1))
        assertTrue(SelectionToolbar.isSelectionActive(4))
    }

    @Test
    fun lockLabelTogglesOnAllLocked() {
        assertEquals(R.string.chat_lock_dialog, SelectionToolbar.lockLabelRes(allLocked = false))
        assertEquals(R.string.chat_unlock_dialog, SelectionToolbar.lockLabelRes(allLocked = true))
    }

    @Test
    fun nonDestructiveActionsKeepTheSelection() {
        assertFalse(SelectionToolbar.clearsSelection(SelectionToolbar.Action.COPY))
        assertFalse(SelectionToolbar.clearsSelection(SelectionToolbar.Action.SHARE))
        assertFalse(SelectionToolbar.clearsSelection(SelectionToolbar.Action.FORWARD))
    }

    @Test
    fun destructiveActionsClearTheSelection() {
        assertTrue(SelectionToolbar.clearsSelection(SelectionToolbar.Action.TRASH))
        assertTrue(SelectionToolbar.clearsSelection(SelectionToolbar.Action.LOCK))
    }

    @Test
    fun moreMenuStaysReachableWhileSeveralAreSelected() {
        assertFalse(SelectionToolbar.showMore(0))
        assertTrue(SelectionToolbar.showMore(1))
        assertTrue(SelectionToolbar.showMore(4))
    }

    @Test
    fun selectAllSkipsLockedMessages() {
        assertEquals(
            listOf(1L, 3L),
            SelectionToolbar.selectAllCandidates(listOf(1L, 2L, 3L, 4L), setOf(2L, 4L))
        )
        assertEquals(
            listOf(1L, 2L),
            SelectionToolbar.selectAllCandidates(listOf(1L, 2L), emptySet())
        )
        assertTrue(SelectionToolbar.selectAllCandidates(listOf(5L), setOf(5L)).isEmpty())
    }
}
