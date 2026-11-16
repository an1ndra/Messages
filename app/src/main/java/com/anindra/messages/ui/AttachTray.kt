package com.anindra.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.anindra.messages.ui.theme.LocalReduceMotion
import com.anindra.messages.ui.theme.Motion
import com.anindra.messages.ui.theme.motionSpring
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anindra.messages.ui.theme.chatBar
import com.anindra.messages.ui.theme.inputPill

/** One action the tray offers, beside the camera tile. */
internal data class AttachAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Tile geometry of the attachment tray, measured from the approved design.
 *
 * The camera column is [REFERENCE_CAMERA_DP] wide against a
 * [REFERENCE_TILE_DP] square tile, and the columns plus their [GAP_DP] gaps
 * fill the content width, so a narrower screen shrinks the grid instead of
 * spilling off it.
 */
internal data class AttachTraySizes(
    val cameraWidth: Dp,
    val tile: Dp,
    val tileHeight: Dp,
    val gap: Dp,
    val pagePad: Dp,
    val verticalPad: Dp
) {
    /** The camera tile spans both rows plus the gap between them. */
    val cameraHeight: Dp get() = tileHeight * 2 + gap

    /** What the tray adds to the chat's height while it is open. */
    val height: Dp get() = cameraHeight + verticalPad * 2
}

internal object AttachTrayMetrics {

    const val GAP_DP = 8f
    const val PAGE_PAD_DP = 8f
    const val VERTICAL_PAD_DP = 8f
    const val REFERENCE_CAMERA_DP = 132f
    const val REFERENCE_TILE_DP = 112f

    /** Columns of tiles beside the camera tile. */
    const val COLUMNS = 2

    /** A tile is slightly wider than it is tall, which keeps the tray short. */
    const val TILE_ASPECT = 0.9f

    /** Fraction of a tile's height the icon sits above its centre. */
    const val ICON_LIFT = 0.07f

    /** The label's distance from the tile's bottom edge, as a fraction of it. */
    const val LABEL_INSET_FRACTION = 0.08f

    /** Icon size and corner radius, as fractions of the tile they sit in. */
    const val ICON_FRACTION = 0.24f
    const val RADIUS_FRACTION = 0.107f

    private const val UNITS = REFERENCE_CAMERA_DP + REFERENCE_TILE_DP * COLUMNS

    /** Content width is the tray's width after [PAGE_PAD_DP] on each side. */
    fun sizes(contentWidthDp: Float): AttachTraySizes {
        val scalable = (contentWidthDp - GAP_DP * 2).coerceAtLeast(1f)
        val scale = scalable / UNITS
        val tileWidth = REFERENCE_TILE_DP * scale
        return AttachTraySizes(
            cameraWidth = (REFERENCE_CAMERA_DP * scale).dp,
            tile = tileWidth.dp,
            tileHeight = (tileWidth * TILE_ASPECT).dp,
            gap = GAP_DP.dp,
            pagePad = PAGE_PAD_DP.dp,
            verticalPad = VERTICAL_PAD_DP.dp
        )
    }
}

/**
 * The attachment tray that takes the keyboard's place.
 *
 * It is not a sheet: it sits at the bottom of the chat's own column, so the
 * conversation above it stays on screen and undimmed while it is open, and the
 * camera tile spans both rows of the grid beside it.
 */
@Composable
internal fun AttachTray(
    onCamera: () -> Unit,
    cameraLabel: String,
    cameraIcon: ImageVector,
    actions: List<AttachAction>,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    // One accent per tile, cycled from the scheme's three so a dynamic palette
    // keeps them related instead of turning the tray into a paint chart.
    val accents = listOf(colors.primary, colors.tertiary, colors.secondary)
    Column(
        modifier
            .fillMaxWidth()
            // The same strip the composer sits in, so the tray reads as the
            // keyboard's slot rather than a card floating over the chat.
            .background(colors.chatBar)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AttachTrayMetrics.PAGE_PAD_DP.dp)
        ) {
            val sizes = AttachTrayMetrics.sizes(maxWidth.value)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = sizes.verticalPad),
                horizontalArrangement = Arrangement.spacedBy(sizes.gap)
            ) {
                AttachTile(
                    label = cameraLabel,
                    icon = cameraIcon,
                    width = sizes.cameraWidth,
                    height = sizes.cameraHeight,
                    onClick = onCamera,
                    iconTint = accents[0]
                )
                Column(verticalArrangement = Arrangement.spacedBy(sizes.gap)) {
                    actions.chunked(AttachTrayMetrics.COLUMNS)
                        .forEachIndexed { rowIndex, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(sizes.gap)) {
                                row.forEachIndexed { column, action ->
                                    val index = rowIndex * AttachTrayMetrics.COLUMNS + column
                                    AttachTile(
                                        label = action.label,
                                        icon = action.icon,
                                        width = sizes.tile,
                                        height = sizes.tileHeight,
                                        onClick = action.onClick,
                                        iconTint = accents[(index + 1) % accents.size]
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}

/** One tile: a Material surface with its icon above the label. */
@Composable
internal fun AttachTile(
    label: String,
    icon: ImageVector,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified
) {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    // A pressed tile gives a little: the ripple alone is easy to miss on a
    // surface this size, and this is what makes a tap feel answered.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = motionSpring(reduceMotion, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "attachTilePress"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(width = width, height = height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(width * AttachTrayMetrics.RADIUS_FRACTION),
        color = colors.surfaceContainerHighest,
        contentColor = colors.onSurface,
        interactionSource = interaction
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (iconTint == Color.Unspecified) colors.onSurface else iconTint,
                modifier = Modifier
                    .size(minOf(width, height) * AttachTrayMetrics.ICON_FRACTION)
                    .offset(y = -(height * AttachTrayMetrics.ICON_LIFT))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = width * AttachTrayMetrics.LABEL_INSET_FRACTION)
            )
        }
    }
}
