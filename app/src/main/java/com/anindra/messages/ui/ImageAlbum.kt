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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Two tiles per row, Google Messages' arrangement: a second picture sits beside
 * the first, and from the fifth on the last tile says how many more there are.
 * The counts are pure so a test can hold the shape without rendering.
 */
object ImageAlbum {
    const val COLUMNS = 2
    const val MAX_TILES = 4

    /** How many pictures get their own tile. */
    fun shown(count: Int): Int = count.coerceIn(0, MAX_TILES)

    /** How many pictures the "+N" tile stands for, zero when every one is tiled. */
    fun overflow(count: Int): Int = (count - MAX_TILES).coerceAtLeast(0)

    /** Pictures on [row], counting from zero. */
    fun rowCount(count: Int, row: Int): Int {
        val tiles = shown(count)
        return (tiles - row * COLUMNS).coerceIn(0, COLUMNS)
    }
}

/** Squares are sized so two tiles plus the 4dp gutter fit the 260dp bubble. */
private val AlbumTile = 126.dp
private val TileCorner = RoundedCornerShape(12.dp)

/**
 * Every picture of one MMS as a grid inside its bubble. Tapping a tile opens
 * that picture; long-pressing any of them selects the message, which is the
 * whole album.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumGrid(
    uris: List<String>,
    isMe: Boolean,
    onImageTap: (String) -> Unit,
    onLongPress: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val tiles = ImageAlbum.shown(uris.size)
    val extra = ImageAlbum.overflow(uris.size)
    val description = stringResource(R.string.chat_album_photos, uris.size)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
        // One node names the whole grid, so a screen reader and a regression
        // script both see "3 photos" instead of three undecorated images.
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        for (row in 0 until (tiles + ImageAlbum.COLUMNS - 1) / ImageAlbum.COLUMNS) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(ImageAlbum.rowCount(uris.size, row)) { column ->
                    val index = row * ImageAlbum.COLUMNS + column
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
                        if (extra > 0 && index == tiles - 1) {
                            // A themed badge rather than a black scrim with white
                            // text: one token pair, legible in both schemes.
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = cs.secondaryContainer,
                                contentColor = cs.onSecondaryContainer,
                                modifier = Modifier.align(Alignment.Center)
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
