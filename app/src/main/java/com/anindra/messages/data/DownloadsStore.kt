package com.anindra.messages.data

import android.content.ContentValues
import android.content.Context
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

    /** Saves an MMS clip into Movies/Messages so it outlives the provider row
     *  (#461). Video has its own MediaStore collection; writing a clip into the
     *  image one makes the gallery skip it. */
    fun writeVideo(context: Context, displayName: String, mime: String, bytes: ByteArray): Boolean {
        return try {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + DIR)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
        } catch (_: Exception) {
            false
        }
    }

    /** Saves a message attachment (e.g. an MMS picture) into the shared
     *  gallery under Pictures/Messages so it outlives the provider row (#235). */
    fun writeImage(context: Context, displayName: String, mime: String, bytes: ByteArray): Boolean {
        return try {
            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + DIR)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: return false
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }
}
