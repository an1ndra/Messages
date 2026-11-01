package com.anindra.messages.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anindra.messages.R
import com.anindra.messages.ui.theme.incomingBubble
import com.anindra.messages.ui.theme.onSelectedBubble
import com.anindra.messages.ui.theme.outgoingBubble
import com.anindra.messages.ui.theme.selectedBubble
import com.anindra.messages.voice.VoiceNotePolicy
import com.anindra.messages.voice.VoicePlayback
import com.anindra.messages.voice.VoiceWaveform

/**
 * A voice note in the conversation, laid out the way Google Messages draws one:
 * play/pause, the recording's waveform with the played part filled in, and the
 * length on the end.
 *
 * The length is measured rather than assumed — an MMS voice note carries no
 * duration column — and the waveform is decoded from the note itself, so a quiet
 * recording looks quiet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VoiceBubble(
    uri: String,
    isMe: Boolean,
    isSelected: Boolean = false,
    body: AnnotatedString = AnnotatedString(""),
    bodyStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onLongPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val state by VoicePlayback.state.collectAsStateWithLifecycle()
    val mine = state.uri == uri
    val playing = mine && state.playing
    var durationMs by remember(uri) { mutableIntStateOf(VoicePlayback.cachedDuration(uri)) }
    var peaks by remember(uri) { mutableStateOf(VoiceWaveform.cached(uri)) }
    LaunchedEffect(uri) {
        if (durationMs <= 0) durationMs = VoicePlayback.measureDuration(context, uri)
        if (peaks == null) peaks = VoiceWaveform.peaks(context, uri)
    }
    val total = if (mine && state.durationMs > 0) state.durationMs else durationMs
    val position = if (mine && (playing || state.positionMs > 0)) state.positionMs else 0
    val progress = if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f
    val bars = peaks ?: VoiceWaveform.placeholder(uri)
    val ink = if (isMe) cs.onPrimaryContainer else cs.onSurface
    val accent = if (isMe) cs.onPrimaryContainer else cs.primary
    val lengthText = if (position > 0) {
        "${VoiceNotePolicy.label(position.toLong())} / ${VoiceNotePolicy.label(total.toLong())}"
    } else {
        VoiceNotePolicy.label(total.toLong())
    }

    Surface(
        color = if (isSelected) cs.selectedBubble else if (isMe) cs.outgoingBubble else cs.incomingBubble,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .widthIn(max = 260.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { VoicePlayback.toggle(context, uri) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (playing) R.string.access_voice_pause else R.string.access_voice_play
                        ),
                        tint = accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(24.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    val gap = size.width / (bars.size * 2f - 1f)
                    val barWidth = gap.coerceAtLeast(1f)
                    val played = progress * bars.size
                    bars.forEachIndexed { index, value ->
                        val height = (value * size.height).coerceAtLeast(2.dp.toPx())
                        val x = index * gap * 2f
                        drawRoundRect(
                            color = if (index < played) accent else accent.copy(alpha = 0.32f),
                            topLeft = Offset(x, (size.height - height) / 2f),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }
                Text(
                    text = lengthText,
                    style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 6.dp, end = 8.dp)
                )
            }
            if (body.text.isNotBlank()) {
                Text(
                    text = body,
                    style = bodyStyle,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 4.dp)
                )
            }
        }
    }
}
