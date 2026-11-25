package com.anindra.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * L'onglet de reprise : la conversation courante reste a un geste quand la
 * feuille est refermee.
 *
 * Il est ancre en bas de l'ecran, colle au bord, et n'a PAS de hauteur propre
 * en dur : il occupe la bande de [SHEET_HANDLE_FRACTION] laissee libre par la
 * feuille. C'est un frere de la feuille dans le meme Box, il ne doit donc pas
 * se dessiner ailleurs.
 */
@Composable
internal fun M3ReopenTab(onReopen: () -> Unit) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    // un tirage vers le haut suffit a rouvrir
                    if (dragAmount < -16f) onReopen()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { onReopen() }
            },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp
    ) {
        // La poignee est calee en haut de la bande, la zone du bas reste vide
        // mais cliquable : c'est la bande de navigation systeme.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 14.dp + navBottom),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                Modifier
                    .size(width = 44.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            )
        }
    }
}
