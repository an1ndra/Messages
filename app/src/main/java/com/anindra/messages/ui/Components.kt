package com.anindra.messages.ui

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.anindra.messages.ui.theme.Motion
import com.anindra.messages.ui.theme.contrastRatio
import com.anindra.messages.R
import com.anindra.messages.ui.theme.LocalReduceMotion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

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

// Google Messages keeps the same bright palette on both schemes and only flips
// the silhouette ink (measured #FFFFFF and #202124 off the running app). Tinting
// the fills toward the theme — #347 round 2 — desaturates them into mud on a
// muted wallpaper palette, so the fills stay in the palette.
private val avatarGlyphInkDark = Color(0xFF202124)

/** WCAG floor for a non-text UI component against its surface. */
internal const val AVATAR_MIN_CONTRAST = 3f

/** The contact's own palette colour, before any contrast consideration. */
fun avatarColor(key: String): Color {
    var h = 0
    for (c in key) h = h * 31 + c.code
    return avatarColors[Math.abs(h) % avatarColors.size]
}

/**
 * The colour this contact actually paints, guaranteed to be readable on
 * [surface].
 *
 * GM ships the palette as-is and lets a chip blend into the page — pink on white
 * measures 2.58:1, cyan 1.78:1 — because chromatic contrast and the silhouette
 * carry the meaning. That works until the wallpaper palette is muted, where a
 * low-chroma entry (brown, indigo, blue-grey) is neither lighter nor a different
 * hue from the page. So the contact keeps its own colour when it passes the
 * floor, and otherwise takes the nearest palette entry that does; the walk is
 * deterministic, so a contact doesn't change colour between recompositions.
 */
internal fun avatarColorOn(key: String, surface: Color): Color {
    val start = avatarColors.indexOf(avatarColor(key)).coerceAtLeast(0)
    var best = avatarColors[start]
    var bestRatio = contrastRatio(best, surface)
    if (bestRatio >= AVATAR_MIN_CONTRAST) return best
    for (step in 1 until avatarColors.size) {
        val candidate = avatarColors[(start + step) % avatarColors.size]
        val ratio = contrastRatio(candidate, surface)
        if (ratio >= AVATAR_MIN_CONTRAST) return candidate
        if (ratio > bestRatio) {
            best = candidate
            bestRatio = ratio
        }
    }
    return best
}

/**
 * The silhouette ink for [fill]: whichever of the near-black and white reads
 * better on that colour. Read from the fill rather than the night mode, because
 * the fill is what it sits on.
 */
internal fun avatarInkOn(fill: Color): Color =
    if (contrastRatio(avatarGlyphInkDark, fill) >= contrastRatio(Color.White, fill)) {
        avatarGlyphInkDark
    } else {
        Color.White
    }

/** Google-Messages style contact avatar: contact photo if available, else silhouette. */
@Composable
fun PersonAvatar(
    key: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    tint: Color? = null,
    /** What the chip is painted on, so the fill can keep a readable contrast. */
    onSurface: Color = MaterialTheme.colorScheme.background
) {
    val fill = backgroundColor ?: avatarColorOn(key, onSurface)
    val glyphTint = tint ?: avatarInkOn(fill)
    val context = LocalContext.current
    val photoUri by produceState<String?>(initialValue = photoUriCache.get(key), key) {
        val hit = photoUriCache.get(key)
        if (hit != null) { value = hit.ifBlank { null }; return@produceState }
        value = withContext(Dispatchers.IO) {
            resolveContactPhotoUri(context, key).also { photoUriCache.put(key, it) }
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .background(fill, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val uri = photoUri
        if (uri != null) {
            coil3.compose.AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_person_placeholder),
                contentDescription = null,
                tint = glyphTint,
                modifier = Modifier.size(size * 0.62f)
            )
        }
    }
}

private val photoUriCache = PhotoUriCache()

/** Resolves the contact's photo URI via ContactsContract (no caching). */
/**
 * The owner's own contact card photo, if they set one on the device. Android
 * keeps it on the profile row of the contacts provider, which is a different
 * cursor from a phone-number lookup.
 */
private fun resolveOwnerPhotoUri(context: Context): String? = try {
    context.contentResolver.query(
        android.provider.ContactsContract.Profile.CONTENT_URI,
        arrayOf(android.provider.ContactsContract.CommonDataKinds.Photo.PHOTO_URI),
        null, null, null
    )?.use { c ->
        if (c.moveToFirst()) c.getString(0)?.ifBlank { null } else null
    }
} catch (_: Exception) {
    null
}

/**
 * The signed-in user's avatar: the device profile picture when there is one,
 * otherwise the themed silhouette it always used. Reading the profile needs
 * READ_CONTACTS, which this app already asks for; without it the query returns
 * nothing and the default is drawn.
 */
@Composable
fun OwnerAvatar(
    size: Dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    tint: Color = MaterialTheme.colorScheme.onPrimary
) {
    val context = LocalContext.current
    val photo by produceState<String?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { resolveOwnerPhotoUri(context) }
    }
    val uri = photo
    if (uri != null) {
        coil3.compose.AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape)
        )
    } else {
        PersonAvatar(
            stringResource(R.string.conversations_me),
            size = size,
            modifier = modifier,
            backgroundColor = backgroundColor,
            tint = tint
        )
    }
}

private fun resolveContactPhotoUri(context: Context, number: String): String? = try {
    val lookupUri = Uri.withAppendedPath(
        ContactsContract.AUTHORITY_URI, "phone_lookup/" + Uri.encode(number)
    )
    context.contentResolver.query(lookupUri, arrayOf("photo_uri"), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0)?.ifBlank { null } else null
    }
} catch (_: Exception) {
    null
}

/** Small briefcase glyph marking a contact that comes from the work profile. */
@Composable
fun WorkProfileBadge(modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.BusinessCenter,
        contentDescription = stringResource(R.string.contact_work_profile),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(14.dp)
    )
}

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun zoned(ts: Long): ZonedDateTime = Instant.ofEpochMilli(ts).atZone(zone)

/** Ambient "now" (epoch millis) that advances while a screen is visible, so
 *  relative labels ("Now", "5 min") age instead of freezing at composition. */
val LocalNowTick = compositionLocalOf { System.currentTimeMillis() }

/** conversationId -> body of its earliest pending scheduled message, for the
 *  "Scheduled: …" conversation-list preview. Empty when nothing is queued. */
val LocalScheduledPreview = compositionLocalOf<Map<Long, String>> { emptyMap() }

fun formatListTime(ts: Long, now: Long = System.currentTimeMillis(), ctx: android.content.Context): String {
    if (ts <= 0) return ""
    return when {
        now - ts < 60_000L -> ctx.getString(R.string.time_now)
        now - ts < 3_600_000L -> String.format(ctx.getString(R.string.time_minutes), (now - ts) / 60_000)
        sameDay(ts, now) -> timeOnlyFormatter(is24HourFormat(ctx)).format(zoned(ts))
        isYesterday(ts) -> ctx.getString(R.string.time_yesterday)
        else -> dateFormatter(DateStyle.Day).format(zoned(ts))
    }
}

fun formatDividerTime(ts: Long, ctx: android.content.Context): String {
    return when {
        sameDay(ts, System.currentTimeMillis()) -> ctx.getString(R.string.time_today)
        isYesterday(ts) -> ctx.getString(R.string.time_yesterday)
        else -> dateFormatter(DateStyle.DayWithAbbrevWeekday).format(zoned(ts))
    }
}

fun formatTimeOnly(ts: Long, is24Hour: Boolean): String =
    timeOnlyFormatter(is24Hour).format(zoned(ts))

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
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .clearAndSetSemantics {},
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
    val reduceMotion = LocalReduceMotion.current
    val translateAnim: Float = if (reduceMotion) {
        0f
    } else {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition()
        val anim: Float by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1200f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    Motion.SHIMMER_DURATION_MS,
                    easing = Motion.emphasized(reduceMotion)
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            )
        )
        anim
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.runtime.compositionLocalOf { 0f } provides translateAnim,
        content = content
    )
}

private val LocalShimmerTranslate = androidx.compose.runtime.compositionLocalOf { 0f }

@Composable
fun SkeletonConversationRow() {
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

/** The style dialog copy paints with: the string's own strong direction on top
 *  of the slot's provided style. A Persian-locale device runs every window RTL
 *  while this app's strings are English (no values-fa yet), and an English
 *  sentence in an RTL paragraph parks its trailing "?" or "." at the visual
 *  start (#345). Logs DialogBidi so the regression scripts can assert the
 *  direction — the paint order itself is invisible to a uiautomator dump. */
internal fun directionalStyle(base: TextStyle, text: String): TextStyle {
    val direction = BidiText.direction(text)
    android.util.Log.d("DialogBidi", "dir=${direction.toTextDirection()} len=${text.length}")
    return base.copy(textDirection = direction.toTextDirection())
}
