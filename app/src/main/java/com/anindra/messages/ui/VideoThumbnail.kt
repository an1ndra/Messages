package com.anindra.messages.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * The first frame of an MMS clip, for the bubble's still (#461).
 *
 * The provider hands a clip out as `content://mms/part/<id>` with no file on
 * disk and no thumbnail column, so the frame is decoded once and cached in the
 * composition. A part the decoder cannot open (a codec the device lacks) simply
 * yields null and the bubble draws its play badge on the placeholder.
 */
internal object VideoThumbnail {

    fun frame(context: Context, uri: String): ImageBitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.asImageBitmap()
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()
}
