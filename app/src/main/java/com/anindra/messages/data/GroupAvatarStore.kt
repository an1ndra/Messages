package com.anindra.messages.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * The picture a group shows instead of its generated avatar.
 *
 * The bytes are copied into the app's own files directory: a photo-picker grant
 * lasts only as long as the process, so storing the picked URI would draw the
 * picture once and then go blank on the next start. The copy is downscaled on
 * the way in — a 12 MP photo is far more than a 96 dp circle can use, and every
 * screen that draws the group would otherwise decode the full-size image.
 */
object GroupAvatarStore {

    /** Longest side of the stored copy. Comfortably above the 96 dp avatar at
     *  the densities this app runs at, and small enough to decode on the fly. */
    const val MAX_PX = 512

    private const val DIR = "group-avatars"
    private const val JPEG_QUALITY = 90

    /**
     * The power-of-two [BitmapFactory.Options.inSampleSize] that decodes no
     * larger than [maxPx] on the longest side, so a big photo is never
     * materialised at full size just to be shrunk afterwards.
     */
    fun sampleSizeFor(width: Int, height: Int, maxPx: Int = MAX_PX): Int {
        if (width <= 0 || height <= 0 || maxPx <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / sample > maxPx) sample *= 2
        return sample
    }

    /** [bitmap] scaled so its longest side is at most [maxPx]; the original is
     *  returned untouched when it already fits. */
    fun scaleDown(bitmap: Bitmap, maxPx: Int = MAX_PX): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxPx) return bitmap
        val scale = maxPx.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    /**
     * Copies [source] into the app's files as this conversation's picture and
     * returns its absolute path, or null when the image cannot be read.
     */
    fun save(context: Context, conversationId: Long, source: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)
            ?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(width, height) }
        val decoded = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val scaled = scaleDown(decoded)
        val directory = File(context.filesDir, DIR).apply { mkdirs() }
        val target = File(directory, "group-$conversationId-${System.currentTimeMillis()}.jpg")
        target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (scaled !== decoded) decoded.recycle()
        scaled.recycle()
        target.absolutePath
    }.getOrNull()

    /** Drops a copy that has been replaced or cleared. */
    fun delete(path: String) {
        if (path.isBlank()) return
        runCatching { File(path).takeIf { it.isFile }?.delete() }
    }
}
