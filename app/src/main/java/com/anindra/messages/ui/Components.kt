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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Google Messages avatar palette
private val avatarColors = listOf(
    Color(0xFFFF63B8), // pink
    Color(0xFFEE675C), // coral red
    Color(0xFFFA903E), // orange
    Color(0xFF4ECDE6), // cyan
    Color(0xFFAF5CF7), // purple
    Color(0xFFFA903E)  // orange
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
    val photo by produceState<Bitmap?>(initialValue = null, key) {
        value = withContext(Dispatchers.IO) { loadContactPhoto(context, key) }
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
                BitmapFactory.decodeStream(input)
            }
        }
    } catch (_: Exception) {
        null
    }
}

private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dayFmt = SimpleDateFormat("MMM d", Locale.getDefault())

private fun startOfDay(cal: Calendar): Calendar =
    (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

fun formatListTime(ts: Long): String {
    if (ts <= 0) return ""
    val now = System.currentTimeMillis()
    return when {
        now - ts < 60_000L -> "Now"
        now - ts < 3_600_000L -> "${(now - ts) / 60_000} min"
        sameDay(ts, now) -> timeFmt.format(Date(ts))
        isYesterday(ts) -> "Yesterday"
        else -> dayFmt.format(Date(ts))
    }
}

fun formatDividerTime(ts: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(ts, now) -> "Today"
        isYesterday(ts) -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(ts))
    }
}

fun formatTimeOnly(ts: Long): String = timeFmt.format(Date(ts))

fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return startOfDay(ca).timeInMillis == startOfDay(cb).timeInMillis
}

private fun isYesterday(ts: Long): Boolean {
    val y = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
    return sameDay(ts, y)
}

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
    val transition = androidx.compose.animation.core.rememberInfiniteTransition()
    val translateAnim: Float by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        )
    )
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
