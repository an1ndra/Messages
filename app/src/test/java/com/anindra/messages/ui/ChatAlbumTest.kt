package com.anindra.messages.ui

import com.anindra.messages.data.Message
import com.anindra.messages.data.ScheduledMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One multi-picture MMS is one message, however many rows it is stored as
 * (#398). These hold the fold that turns those rows back into a single bubble.
 */
class ChatAlbumTest {

    private fun message(
        id: Long,
        sysId: Long = 0,
        mediaType: String = "image",
        transport: String = "mms",
        timestamp: Long = 1_000L,
        body: String = ""
    ) = Message(
        id = id,
        conversationId = 1,
        body = body,
        timestamp = timestamp,
        isMe = false,
        status = "received",
        mediaType = mediaType,
        mediaUri = if (mediaType == "text") "" else "content://mms/part/$id",
        transport = transport,
        sysId = sysId
    )

    @Test
    fun picturesOfOneProviderMmsBecomeOneRow() {
        val rows = listOf(
            ChatRow.Sent(message(1, sysId = 90, body = "Caption")),
            ChatRow.Sent(message(2, sysId = 90)),
            ChatRow.Sent(message(3, sysId = 90))
        )
        val folded = foldAlbums(rows)
        assertEquals(1, folded.size)
        val album = folded.single() as ChatRow.Album
        assertEquals(listOf(1L, 2L, 3L), album.messages.map { it.id })
        assertEquals("Caption", album.first.body)
        assertEquals("album_90", album.key)
    }

    @Test
    fun twoProviderMmsDoNotShareABubble() {
        val rows = listOf(
            ChatRow.Sent(message(1, sysId = 90)),
            ChatRow.Sent(message(2, sysId = 90)),
            ChatRow.Sent(message(3, sysId = 91)),
            ChatRow.Sent(message(4, sysId = 91))
        )
        val folded = foldAlbums(rows)
        assertEquals(listOf("album_90", "album_91"), folded.map { it.key })
    }

    @Test
    fun aSinglePictureStaysAnOrdinaryBubble() {
        val folded = foldAlbums(listOf(ChatRow.Sent(message(1, sysId = 90))))
        assertTrue(folded.single() is ChatRow.Sent)
    }

    @Test
    fun anSmsIsNeverPartOfAnAlbum() {
        // sys_id is the provider's; an SMS has none, and an MMS's voice note is
        // a message of its own rather than another tile.
        assertNull(albumKey(message(1, sysId = 0)))
        assertNull(albumKey(message(1, sysId = 90, mediaType = "audio")))
        assertNull(albumKey(message(1, sysId = 90, mediaType = "text", transport = "sms")))
        assertEquals(90L, albumKey(message(1, sysId = 90)))
    }

    @Test
    fun anAlbumFoldsEvenWhenAnotherMessageSortsBetweenItsPictures() {
        // Every row of one MMS carries the same timestamp, so a message in that
        // second can sit between two of its pictures. The album must stay one
        // message — adjacency let it split into two `LazyColumn` items with the
        // same key, which Compose rejects outright.
        val rows = listOf(
            ChatRow.Sent(message(1, sysId = 90)),
            ChatRow.Sent(message(2, sysId = 0, mediaType = "text", transport = "sms")),
            ChatRow.Sent(message(3, sysId = 90))
        )
        val folded = foldAlbums(rows)
        assertEquals(2, folded.size)
        val album = folded[0] as ChatRow.Album
        assertEquals(listOf(1L, 3L), album.messages.map { it.id })
        assertEquals(2L, (folded[1] as ChatRow.Sent).message.id)
    }

    @Test
    fun aScheduledRowBetweenPicturesDoesNotSplitTheAlbum() {
        val rows = listOf(
            ChatRow.Sent(message(1, sysId = 90)),
            ChatRow.Pending(
                ScheduledMessage(
                    id = 5,
                    address = "+15551230000",
                    body = "later",
                    timestamp = 1_000L,
                    conversationId = 1
                )
            ),
            ChatRow.Sent(message(3, sysId = 90))
        )
        val folded = foldAlbums(rows)
        assertEquals(2, folded.size)
        assertEquals(listOf(1L, 3L), (folded[0] as ChatRow.Album).messages.map { it.id })
        assertTrue(folded[1] is ChatRow.Pending)
    }

    @Test
    fun interleavedAlbumsKeepDistinctKeys() {
        val rows = listOf(
            ChatRow.Sent(message(1, sysId = 90)),
            ChatRow.Sent(message(2, sysId = 91)),
            ChatRow.Sent(message(3, sysId = 90)),
            ChatRow.Sent(message(4, sysId = 91))
        )
        val folded = foldAlbums(rows)
        val keys = folded.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
        assertEquals(listOf("album_90", "album_91"), keys)
    }

    @Test
    fun theAlbumGridNeverGrowsPastFourTiles() {
        assertEquals(0, ImageAlbum.shown(0))
        assertEquals(1, ImageAlbum.shown(1))
        assertEquals(3, ImageAlbum.shown(3))
        assertEquals(4, ImageAlbum.shown(9))
        assertEquals(0, ImageAlbum.overflow(4))
        assertEquals(5, ImageAlbum.overflow(9))
        assertEquals(2, ImageAlbum.rowCount(3, 0))
        assertEquals(1, ImageAlbum.rowCount(3, 1))
        assertEquals(0, ImageAlbum.rowCount(3, 2))
    }

    @Test
    fun anAlbumStandsForEveryMessageIdItHolds() {
        val album = ChatRow.Album(listOf(message(1, sysId = 90), message(2, sysId = 90)))
        assertEquals(listOf(1L, 2L), album.messageIds())
        assertEquals(1L, album.firstMessage()?.id)
        assertNull(ChatRow.Pending(
            ScheduledMessage(1, "+15551230000", "later", 1_000L, 1)
        ).firstMessage())
    }
}
