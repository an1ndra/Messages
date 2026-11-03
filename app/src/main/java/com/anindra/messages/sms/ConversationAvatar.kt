package com.anindra.messages.sms

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.compose.ui.graphics.toArgb
import com.anindra.messages.R
import com.anindra.messages.ui.avatarColor

/**
 * The picture a conversation shows outside the app — in its notification, in the
 * notification panel's avatar and in its bubble (#354 follow-up).
 *
 * Every conversation used to get the launcher icon, so all bubbles looked like
 * the app instead of the person. This mirrors what [com.anindra.messages.ui.PersonAvatar]
 * draws in the list: the contact photo when there is one, else the same
 * silhouette on the same palette colour for that address.
 *
 * Both are handed over as *adaptive* bitmap icons, so the launcher/bubble host
 * masks them into whatever shape the device uses (circle, squircle, rounded
 * square) instead of baking one shape into the pixels.
 */
object ConversationAvatar {

    /** Adaptive-icon grid: 108dp, of which the centre 72dp is guaranteed visible. */
    const val SIZE_PX = 108

    private const val SAFE_FRACTION = 72f / 108f

    /** Where the picture came from, for Diagnostics and the regression script. */
    enum class Source { PHOTO, AVATAR }

    /** [bitmap] is kept alongside: NotificationCompat wants a Bitmap for the
     *  large icon while shortcuts and bubble metadata want the IconCompat. */
    class Icon(
        val icon: IconCompat,
        val bitmap: Bitmap,
        val source: Source,
        val shade: Int
    )

    /**
     * A contact photo can be added after the first notification, so the cache
     * expires rather than pinning the icon for the process's lifetime.
     */
    private class Entry(val icon: Icon, val at: Long)

    private const val TTL_MS = 10 * 60_000L
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    fun forAddress(context: Context, address: String): Icon {
        val now = System.currentTimeMillis()
        cache[address]?.let { if (now - it.at < TTL_MS) return it.icon }
        val icon = build(context, address)
        cache[address] = Entry(icon, now)
        return icon
    }

    private fun build(context: Context, address: String): Icon {
        val shade = avatarColor(address).toArgb()
        val photo = photoBitmap(context, address)
        val bitmap = photo ?: avatarBitmap(context, shade)
        val icon = Icon(
            IconCompat.createWithAdaptiveBitmap(bitmap),
            bitmap,
            if (photo != null) Source.PHOTO else Source.AVATAR,
            shade
        )
        android.util.Log.d(
            "BubbleIcon",
            "address=$address source=${icon.source} shade=#%06X".format(shade and 0xFFFFFF)
        )
        return icon
    }

    /**
     * The palette colour as a full-bleed square with the app's silhouette in the
     * safe zone: any mask the platform applies keeps the glyph whole.
     */
    private fun avatarBitmap(context: Context, shade: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(shade)
        ContextCompat.getDrawable(context, R.drawable.ic_person_placeholder)?.let { glyph ->
            val side = (SIZE_PX * SAFE_FRACTION * 0.62f).toInt()
            val inset = (SIZE_PX - side) / 2
            glyph.setBounds(inset, inset, inset + side, inset + side)
            glyph.setTint(android.graphics.Color.WHITE)
            glyph.draw(canvas)
        }
        return bitmap
    }

    /**
     * The contact's own photo, scaled to the icon grid. A missing contact, a
     * revoked READ_CONTACTS or a contact without a picture all mean "no photo"
     * rather than an error — the notification must still post.
     */
    private fun photoBitmap(context: Context, address: String): Bitmap? = try {
        val id = contactId(context, address)
        val bitmap = id?.let { contactId ->
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            ContactsContract.Contacts.openContactPhotoInputStream(
                context.contentResolver, uri, true
            )?.use { BitmapFactory.decodeStream(it) }
        }
        bitmap?.let { scaled(it) }
    } catch (_: Exception) {
        null
    }

    private fun contactId(context: Context, address: String): Long? {
        val lookup = Uri.withAppendedPath(
            ContactsContract.AUTHORITY_URI, "phone_lookup/" + Uri.encode(address)
        )
        return context.contentResolver
            .query(lookup, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    /** Centre-crops to a square and scales to the adaptive grid. */
    private fun scaled(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(
            source,
            (source.width - side) / 2,
            (source.height - side) / 2,
            side,
            side
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val target = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        Canvas(target).drawBitmap(
            square,
            null,
            android.graphics.Rect(0, 0, SIZE_PX, SIZE_PX),
            paint
        )
        return target
    }
}
