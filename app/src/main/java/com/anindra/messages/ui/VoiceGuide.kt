package com.anindra.messages.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anindra.messages.R
import com.anindra.messages.ui.theme.inputPill

/**
 * The one-time card that teaches the record gesture.
 *
 * A press-and-hold button with two hidden exits — slide left to drop the note,
 * swipe up to lock it — is not something a first-time user can be expected to
 * find. It appears once, above the composer, and never again after the user has
 * dismissed it.
 */
@Composable
internal fun VoiceGuideCard(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.inputPill,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.voice_guide_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.size(8.dp))
            GuideRow(
                icon = { Icon(painterResource(R.drawable.ic_voice_wave), null, Modifier.size(20.dp)) },
                text = stringResource(R.string.voice_guide_hold)
            )
            GuideRow(
                icon = { Icon(Icons.Rounded.ChevronLeft, null, Modifier.size(20.dp)) },
                text = stringResource(R.string.voice_guide_slide)
            )
            GuideRow(
                icon = { Icon(Icons.Outlined.Lock, null, Modifier.size(20.dp)) },
                text = stringResource(R.string.voice_guide_lock)
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.voice_guide_dismiss))
                }
            }
        }
    }
}

@Composable
internal fun GuideRow(icon: @Composable () -> Unit, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
