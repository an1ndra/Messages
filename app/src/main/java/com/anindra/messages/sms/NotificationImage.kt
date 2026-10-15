package com.anindra.messages.sms

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Makes the picture of a received MMS showable in a notification.
 *
 * The picture lives behind `content://mms/part/<id>` (or an app-private file),
 * and the system UI process has no permission to read either. A notification
 * that points at such a URI therefore renders no picture at all. So the image
 * is copied, downscaled, into this app's cache and exposed through the app's
 * FileProvider, and read access is granted to the system UI.
 */
object NotificationImage {
    private const val DIR = "notif"
    private const val MAX_EDGE = 1024
    private const val JPEG_QUALITY = 85
    private const val MAX_FILES = 8
    const val MIME = "image/jpeg"

    /** Packages that draw notifications. One of them is the platform UI; the
     *  grant to a package that is not installed is harmless. */
    private val RENDERERS = listOf("com.android.systemui")

    /** A URI the system UI can read for [source], or null when the picture
     *  cannot be read or decoded (the caller then falls back to a text label). */
    fun prepare(context: Context, source: String): Uri? = try {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val out = File(dir, "n_${source.hashCode().toUInt()}.jpg")
        if (!out.exists() || out.length() == 0L) {
            val bitmap = decode(context, Uri.parse(source)) ?: return null
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bitmap.recycle()
            trim(dir, keep = out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        RENDERERS.forEach {
            try {
                context.grantUriPermission(it, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
        }
        uri
    } catch (_: Throwable) {
        null
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Keeps the cache bounded: only the newest few notification images matter. */
    private fun trim(dir: File, keep: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_FILES).filter { it != keep }.forEach { it.delete() }
    }
}
