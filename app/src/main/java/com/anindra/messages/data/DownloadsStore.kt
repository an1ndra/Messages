package com.anindra.messages.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

object DownloadsStore {
    const val DIR = "Messages"

    fun write(context: Context, displayName: String, mime: String, bytes: ByteArray): Boolean = try {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        resolver.delete(collection, "${MediaStore.Downloads.DISPLAY_NAME}=?", arrayOf(displayName))
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + DIR)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: Exception) {
        false
    }

    /** Where a saved attachment lands, and the collection that accepts it. */
    data class Destination(val collection: Uri, val relativePath: String)

    /**
     * Pictures go to Pictures/Messages and voice notes to Music/Messages.
     *
     * The collection has to follow the content: MediaStore rejects an
     * `audio/mp4` insert into Images, which is how saving a voice note used to
     * fail silently under a "Save image" menu item (#364).
     */
    fun destinationFor(target: MmsSupport.SaveTarget): Destination = when (target) {
        MmsSupport.SaveTarget.IMAGE -> Destination(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            "${Environment.DIRECTORY_PICTURES}/$DIR"
        )
        MmsSupport.SaveTarget.AUDIO -> Destination(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            "${Environment.DIRECTORY_MUSIC}/$DIR"
        )
        MmsSupport.SaveTarget.FILE -> Destination(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${Environment.DIRECTORY_DOWNLOADS}/$DIR"
        )
    }

    /** Saves a message attachment next to the other media of its kind, so it
     *  outlives the provider row. The folder is the one [destinationFor] names. */
    fun writeMedia(
        context: Context,
        displayName: String,
        mime: String,
        bytes: ByteArray,
        target: MmsSupport.SaveTarget
    ): Boolean = try {
        val resolver = context.contentResolver
        val destination = destinationFor(target)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, destination.relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(destination.collection, values) ?: return false
        val written = resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        written
    } catch (_: Exception) {
        false
    }
}
