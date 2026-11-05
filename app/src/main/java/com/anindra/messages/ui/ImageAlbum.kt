package com.anindra.messages.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.anindra.messages.R

/**
 * Layout of the pictures one multi-picture MMS carries, which share a single
 * bubble instead of arriving as one message each (#398 follow-up).
 *
 * Two tiles per row, four pictures per page: the last tile of a page carries a
 * "+N" badge for the pictures on the next page, so a large album is read four
 * at a time rather than one picture with a note about the others.
 * The counts are pure so a test can hold the shape without rendering.
 */
object ImageAlbum {
    const val COLUMNS = 2
    const val PAGE_TILES = 4

    /** Pages a [count]-picture album occupies; zero when it has no picture. */
    fun pageCount(count: Int): Int =
        if (count <= 0) 0 else (count + PAGE_TILES - 1) / PAGE_TILES

    /** First picture on [page], counting from zero. */
    fun pageStart(page: Int): Int = page * PAGE_TILES

    /** Pictures that get their own tile on [page]. */
    fun pageSize(count: Int, page: Int): Int =
        (count - page * PAGE_TILES).coerceIn(0, PAGE_TILES)

    /** Pictures the "+N" badge on [page] stands for: the next page's pictures. */
    fun nextPageCount(count: Int, page: Int): Int = pageSize(count, page + 1)

    /** Tiles on [row] of [page], counting from zero. */
    fun rowCount(count: Int, page: Int, row: Int): Int =
        (pageSize(count, page) - row * COLUMNS).coerceIn(0, COLUMNS)

    /** Rows [page] occupies. */
    fun rows(count: Int, page: Int): Int =
        (pageSize(count, page) + COLUMNS - 1) / COLUMNS
}

/** Squares are sized so two tiles plus the 4dp gutter fit the 260dp bubble. */
private val AlbumTile = 126.dp
private val TileCorner = RoundedCornerShape(12.dp)

/**
 * Every picture of one MMS as a grid inside its bubble. Tapping a tile opens
 * that picture; the badge on the last tile moves to the next page of four, and
 * a page after the first carries a badge back to the previous one. Long-
 * pressing any tile selects the message, which is the whole album.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumGrid(
    uris: List<String>,
    isMe: Boolean,
    onImageTap: (String) -> Unit,
    onLongPress: () -> Unit = {}
) {
    var page by remember(uris) { mutableIntStateOf(0) }
    val lastPage = (ImageAlbum.pageCount(uris.size) - 1).coerceAtLeast(0)
    val current = page.coerceIn(0, lastPage)
    val start = ImageAlbum.pageStart(current)
    val tiles = ImageAlbum.pageSize(uris.size, current)
    val extra = ImageAlbum.nextPageCount(uris.size, current)
    val cs = MaterialTheme.colorScheme
    val previousDescription = stringResource(R.string.chat_album_previous)
    val moreDescription = stringResource(R.string.chat_album_more, extra)
    val description = stringResource(R.string.chat_album_photos, uris.size)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
        // One node names the whole grid, so a screen reader and a regression
        // script both see "3 photos" instead of three undecorated images.
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        for (row in 0 until ImageAlbum.rows(uris.size, current)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(ImageAlbum.rowCount(uris.size, current, row)) { column ->
                    val index = start + row * ImageAlbum.COLUMNS + column
                    Box(Modifier.size(AlbumTile).clip(TileCorner)) {
                        AsyncImage(
                            model = uris[index],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = { onImageTap(uris[index]) },
                                    onLongClick = onLongPress
                                )
                        )
                        if (current > 0 && index == start) {
                            Surface(
                                onClick = { page = current - 1 },
                                shape = RoundedCornerShape(50),
                                color = cs.secondaryContainer,
                                contentColor = cs.onSecondaryContainer,
                                modifier = Modifier.align(Alignment.TopStart).semantics {
                                    contentDescription =
                                        previousDescription
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.padding(6.dp).size(20.dp)
                                )
                            }
                        }
                        if (extra > 0 && index == start + tiles - 1) {
                            // A themed badge rather than a black scrim with white
                            // text: one token pair, legible in both schemes.
                            Surface(
                                onClick = { page = current + 1 },
                                shape = RoundedCornerShape(50),
                                color = cs.secondaryContainer,
                                contentColor = cs.onSecondaryContainer,
                                modifier = Modifier.align(Alignment.Center).semantics {
                                    contentDescription = moreDescription
                                }
                            ) {
                                Text(
                                    text = "+$extra",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
