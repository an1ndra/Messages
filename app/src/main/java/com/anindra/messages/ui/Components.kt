package com.anindra.messages.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.anindra.messages.R
import com.anindra.messages.data.DemoData
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import android.util.LruCache
import java.util.Locale

object BitmapCache {
    // ~24 MiB cap in bytes; prior sizeOf=1 cap actually held ~140 MB of bitmaps
    private val maxBytes = (24 * 1024 * 1024)
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) oldValue.recycle()
        }
    }
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
    fun trimToSize(max: Int = 0) { cache.trimToSize(max) }
}

// Google Messages avatar palette
private val avatarColors = listOf(
    Color(0xFFFF63B8), // pink
    Color(0xFFEE675C), // coral red
    Color(0xFFFA903E), // orange
    Color(0xFF4ECDE6), // cyan
    Color(0xFFAF5CF7), // purple
    Color(0xFF4CAF50), // green
    Color(0xFF2196F3), // blue
    Color(0xFFFF9800), // amber
    Color(0xFF9C27B0), // deep purple
    Color(0xFF00BCD4), // teal
    Color(0xFFE91E63), // rose
    Color(0xFF3F51B5), // indigo
    Color(0xFF009688), // mint
    Color(0xFFFF5722), // deep orange
    Color(0xFF795548), // brown
    Color(0xFF607D8B)  // blue grey
)

val GoogleBlue = Color(0xFF1A73E8)

fun avatarColor(key: String): Color {
    var h = 0
    for (c in key) h = h * 31 + c.code
    return avatarColors[Math.abs(h) % avatarColors.size]
}

/** Google-Messages style contact avatar: contact photo if available, else silhouette. */
@Composable
fun PersonAvatar(
    key: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    tint: Color? = null
) {
    val context = LocalContext.current
    val cached = remember(key) { BitmapCache.get(key) }
    val photo by produceState<Bitmap?>(initialValue = cached, key) {
        val hit = BitmapCache.get(key)
        if (hit != null) { value = hit; return@produceState }
        value = withContext(Dispatchers.IO) {
            loadContactPhoto(context, key).also { bmp ->
                if (bmp != null) BitmapCache.put(key, bmp)
            }
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .background(backgroundColor ?: avatarColor(key), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val bmp = photo
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_person_placeholder),
                contentDescription = null,
                tint = tint ?: Color.Unspecified,
                modifier = Modifier.size(size * 0.62f)
            )
        }
    }
}

/** Loads the contact's profile photo thumbnail for a phone number, or null. */
private fun loadContactPhoto(context: Context, number: String): Bitmap? {
    val demoRes = DemoData.AVATAR_MAP[number]
    if (demoRes != null) {
        return BitmapFactory.decodeResource(context.resources, demoRes)
    }
    return try {
        val resolver = context.contentResolver
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.AUTHORITY_URI, "phone_lookup/" + Uri.encode(number)
        )
        resolver.query(lookupUri, arrayOf("photo_uri"), null, null, null)?.use { c ->
            val photoUri = if (c.moveToFirst()) c.getString(0) else null
            if (photoUri.isNullOrBlank()) null
            else resolver.openInputStream(Uri.parse(photoUri))?.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                val req = 144 * context.resources.displayMetrics.densityDpi / 160
                var sample = 1
                while (opts.outWidth / sample > req * 2 || opts.outHeight / sample > req * 2) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                resolver.openInputStream(Uri.parse(photoUri))?.use { s ->
                    BitmapFactory.decodeStream(s, null, decodeOpts)
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val dividerFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun zoned(ts: Long): ZonedDateTime = Instant.ofEpochMilli(ts).atZone(zone)

fun formatListTime(ts: Long): String {
    if (ts <= 0) return ""
    val now = System.currentTimeMillis()
    return when {
        now - ts < 60_000L -> "Now"
        now - ts < 3_600_000L -> "${(now - ts) / 60_000} min"
        sameDay(ts, now) -> timeFmt.format(zoned(ts))
        isYesterday(ts) -> "Yesterday"
        else -> dayFmt.format(zoned(ts))
    }
}

fun formatDividerTime(ts: Long): String {
    return when {
        sameDay(ts, System.currentTimeMillis()) -> "Today"
        isYesterday(ts) -> "Yesterday"
        else -> dividerFmt.format(zoned(ts))
    }
}

fun formatTimeOnly(ts: Long): String = timeFmt.format(zoned(ts))

private fun epochDay(ts: Long): Long {
    val offset = zone.rules.getOffset(Instant.ofEpochMilli(ts))
    return (ts + offset.totalSeconds * 1000L) / 86400000L
}

fun sameDay(a: Long, b: Long): Boolean = epochDay(a) == epochDay(b)

private fun isYesterday(ts: Long): Boolean =
    epochDay(ts) == epochDay(System.currentTimeMillis()) - 1

@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    }
}

@Composable
fun Modifier.shimmer(): Modifier {
    // one transition shared across the screen, not 40 per-row animations
    val translateAnim = LocalShimmerTranslate.current
    return this.background(
        brush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
            start = androidx.compose.ui.geometry.Offset(translateAnim - 300f, 0f),
            end = androidx.compose.ui.geometry.Offset(translateAnim, 0f)
        )
    )
}

/** Provides the single shared shimmer offset for the current composition.
 *  Wrap a screen or list of skeleton rows with this so each row does not start
 *  its own [rememberInfiniteTransition]. */
@Composable
fun ProvideShimmer(content: @Composable () -> Unit) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition()
    val translateAnim: Float by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                1100,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        )
    )
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.runtime.compositionLocalOf { 0f } provides translateAnim,
        content = content
    )
}

private val LocalShimmerTranslate = androidx.compose.runtime.compositionLocalOf { 0f }

@Composable
fun SkeletonConversationRow() {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .shimmer()
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.45f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .shimmer()
            )
        }
    }
}
