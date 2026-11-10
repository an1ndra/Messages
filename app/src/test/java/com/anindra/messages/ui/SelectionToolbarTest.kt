package com.anindra.messages.ui

import com.anindra.messages.R
import com.anindra.messages.data.Message
import com.anindra.messages.data.MmsSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            SelectionToolbar.selectAllCandidates(
                listOf(listOf(1L), listOf(2L), listOf(3L), listOf(4L)), setOf(2L, 4L)
            )
        )
        assertEquals(
            listOf(1L, 2L),
            SelectionToolbar.selectAllCandidates(listOf(listOf(1L), listOf(2L)), emptySet())
        )
        assertTrue(SelectionToolbar.selectAllCandidates(listOf(listOf(5L)), setOf(5L)).isEmpty())
    }

    @Test
    fun selectAllTakesAWholeAlbumOrNoneOfIt() {
        // A locked picture means the album cannot be bulk-deleted; selecting
        // only its siblings left the bubble looking unselected while the delete
        // still took them.
        val albumAndSms = listOf(listOf(2L, 3L, 4L), listOf(5L))
        assertEquals(
            listOf(5L),
            SelectionToolbar.selectAllCandidates(albumAndSms, setOf(3L))
        )
        assertEquals(
            listOf(2L, 3L, 4L, 5L),
            SelectionToolbar.selectAllCandidates(albumAndSms, emptySet())
        )
    }

    @Test
    fun severalPicturesReadAsPlural() {
        // #398: selecting two pictures wrote both but still offered
        // "Save image", which read as if only one would be saved.
        assertEquals(
            R.string.chat_save_images,
            SelectionToolbar.saveLabelRes(MmsSupport.SaveTarget.IMAGE, count = 2)
        )
        assertEquals(
            R.string.chat_save_images,
            SelectionToolbar.saveLabelRes(MmsSupport.SaveTarget.IMAGE, count = 5)
        )
        assertEquals(
            R.string.chat_save_image,
            SelectionToolbar.saveLabelRes(MmsSupport.SaveTarget.IMAGE, count = 1)
        )
        // A voice note is one note, so the plural rule never reaches it.
        assertEquals(
            R.string.chat_save_voice,
            SelectionToolbar.saveLabelRes(MmsSupport.SaveTarget.AUDIO, count = 3)
        )
    }

    @Test
    fun saveCoversEverySelectedRowWithAMediaUri() {
        fun message(id: Long, mediaType: String, mediaUri: String) = Message(
            id = id,
            conversationId = 1,
            body = "",
            timestamp = id,
            isMe = true,
            status = "sent",
            mediaType = mediaType,
            mediaUri = mediaUri,
        )
        val chat = listOf(
            message(1, "image", "content://mms/part/1"),
            message(2, "image", "content://mms/part/2"),
            message(3, "image", "content://mms/part/3"),
            message(4, "text", ""),
            message(5, "image", ""),
        )

        // One multi-picture album is three rows; Save must keep all three, and
        // must still ignore a text message and a row without a file.
        assertEquals(
            listOf(1L, 2L, 3L),
            SelectionToolbar.saveTargets(chat, setOf(1L, 2L, 3L, 4L, 5L)).map { it.id }
        )
        assertEquals(
            listOf(2L),
            SelectionToolbar.saveTargets(chat, setOf(2L)).map { it.id }
        )
        assertTrue(SelectionToolbar.saveTargets(chat, setOf(4L, 5L)).isEmpty())
    }

    @Test
    fun aVoiceNoteIsOfferedAsAVoiceMessageNotAnImage() {
        // #364: the overflow offered "Save image" on a voice note, and the
        // save then wrote audio bytes into the gallery.
        assertEquals(
            MmsSupport.SaveTarget.AUDIO,
            SelectionToolbar.saveTargetFor("audio")
        )
        assertEquals(R.string.chat_save_voice, SelectionToolbar.saveLabelRes(MmsSupport.SaveTarget.AUDIO, count = 1))
    }

    @Test
    fun aPictureKeepsTheImageLabel() {
        assertEquals(
            MmsSupport.SaveTarget.IMAGE,
            SelectionToolbar.saveTargetFor("image")
        )
        assertEquals(R.string.chat_save_image, SelectionToolbar.saveLabelRes(MmsSupport.SaveTarget.IMAGE))
    }

    @Test
    fun textAndFilesOfferNoSave() {
        assertNull(SelectionToolbar.saveTargetFor("text"))
        assertNull(SelectionToolbar.saveTargetFor("file"))
        assertNull(SelectionToolbar.saveTargetFor(""))
    }
}
