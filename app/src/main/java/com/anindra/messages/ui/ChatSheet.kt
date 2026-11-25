package com.anindra.messages.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Hauteur de la feuille de conversation, en fraction de l'ecran.
 *
 * Fixe : 85%, toujours, qu'il y ait un message ou mille. Une feuille qui
 * change de taille selon le contenu donne une app qui hesite.
 */
internal const val SHEET_OPEN_FRACTION = 0.85f

/**
 * Ce qui reste de la feuille quand elle est refermee : 10% de l'ecran, ancres
 * en BAS. C'est cet onglet qui rend la conversation courante accessible sans
 * repasser par la liste.
 */
internal const val SHEET_HANDLE_FRACTION = 0.10f

/**
 * L'ecran de conversation en feuille ancree au bas de la page.
 *
 * Geometrie, et c'est la seule chose qui compte ici :
 *
 *   - ouverte  : la feuille monte a 85%, son bord bas touche le bas de l'ecran
 *   - refermee : la feuille descend de (85% - 10%), il reste exactement sa
 *                bande haute visible = 10% colles en bas de l'ecran
 *
 * Elle ne sort JAMAIS de l'ecran et ne monte jamais en haut : l'onglet de
 * reprise est la feuille elle-meme, pas un composant separe. C'est ce qui
 * evitait d'avoir un onglet flottant mal ancre.
 *
 * Pas de fond plein ecran non plus : quand la feuille est ouverte on doit
 * voir la liste des conversations derriere elle.
 *
 * La barre de navigation n'est PAS ici : elle appartient au menu principal
 * (la liste), ou elle flotte en bas de l'ecran. Dans la discussion, elle
 * n'a rien a faire.
 */
@Composable
internal fun ChatSheet(
    onDismiss: () -> Unit,
    onReopen: () -> Unit,
    open: Boolean,
    content: @Composable () -> Unit = {}
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Clavier : la feuille remonte pour que la saisie reste au-dessus.
    val imeBottom = com.anindra.messages.ui.imeBottomDp()
    val keyboardLift = (imeBottom - navBottom).coerceAtLeast(0.dp)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenPx = with(density) { maxHeight.toPx() }
        val openPx = screenPx * SHEET_OPEN_FRACTION
        val handlePx = screenPx * SHEET_HANDLE_FRACTION

        // Position de repos quand la feuille est refermee : on la descend
        // juste assez pour qu'il ne reste que la bande de 10% en bas.
        val collapsedPx = openPx - handlePx

        val liftPx = with(density) { keyboardLift.toPx() }

        // 0 = ouverte, collapsedPx = onglet seul ancre en bas.
        // On demarre toujours en position basse : ouvrir la feuille la fait
        // glisser vers le haut au lieu d'apparaitre d'un coup.
        val drag = remember { Animatable(collapsedPx) }

        // Le contenu (l'ecran de discussion) n'est compose que pendant que la
        // feuille est visible. Sinon il resterait monte hors ecran et
        // intercepterait les clics sur la liste des conversations.
        var contentAlive by remember { mutableStateOf(open) }

        LaunchedEffect(open, openPx) {
            if (open) contentAlive = true
            drag.animateTo(
                targetValue = if (open) 0f else collapsedPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            if (!open) contentAlive = false
        }

        // Assombrissement : uniquement la partie recouverte, plafonne a 18%.
        // Assez pour detacher la feuille, jamais assez pour cacher la liste.
        val covered = ((openPx - drag.value).coerceAtLeast(0f)) / openPx
        val dim = (covered * 0.18f).coerceIn(0f, 0.18f)
        if (dim > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = dim))
            )
        }

        // ── la feuille ────────────────────────────────────────────────────
        // Un seul Surface, ancre en bas, qui ne sort jamais completement.
        // Quand elle est refermee sa bande haute reste visible : c'est
        // l'onglet de reprise, et il est en bas par construction.
        val tapToReopen = if (!open) {
            Modifier.pointerInput(Unit) {
                detectTapGestures { onReopen() }
            }
        } else {
            Modifier
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { openPx.toDp() })
                .offset { androidx.compose.ui.unit.IntOffset(0, (drag.value - liftPx).roundToInt()) }
                .then(tapToReopen),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(Modifier.fillMaxSize()) {

                // poignee : c'est elle qu'on attrape
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    drag.snapTo((drag.value + delta).coerceIn(0f, collapsedPx))
                                }
                            },
                            onDragStopped = { velocity ->
                                val target = when {
                                    velocity > 900f -> collapsedPx
                                    velocity < -900f -> 0f
                                    drag.value > collapsedPx / 2f -> collapsedPx
                                    else -> 0f
                                }
                                scope.launch {
                                    drag.animateTo(
                                        target,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    // La decision de route se prend APRES le
                                    // geste : on ne demonte pas la feuille
                                    // au milieu de son animation.
                                    if (target == collapsedPx) onDismiss() else onReopen()
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    )
                }

                // contenu de la discussion : il occupe toute la feuille sous
                // la poignee. La barre de navigation vit dans le menu
                // principal, pas dans la discussion.
                if (contentAlive) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                    ) {
                        content()
                    }
                } else {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Wrapper de compatibilite : l'ancien nom, feuille ouverte. */
@Composable
internal fun TaskSheet(fraction: Float, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ChatSheet(
        onDismiss = onDismiss,
        onReopen = {},
        open = true,
        content = content
    )
}
