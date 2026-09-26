package com.anindra.messages.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anindra.messages.ui.theme.LocalReduceMotion
import com.anindra.messages.ui.theme.Motion
import com.anindra.messages.ui.theme.motionTween
/** A single option in an [ExpressiveTabs] row. */
data class ExpressiveTab(val labelRes: Int, val icon: ImageVector? = null)

/**
 * Connected single-select tab row shared by Spam & blocked and Trash: equal-width
 * buttons with a small gap, the active pill fully rounded on its outer edge while
 * the inactive inner edge stays less rounded — the GM-style M3 switcher.
 */
@Composable
fun ExpressiveTabs(
    tabs: List<ExpressiveTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            ConnectedTab(
                icon = tab.icon,
                label = stringResource(tab.labelRes),
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = connectedShape(index, tabs.lastIndex, selected == index),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RowScope.ConnectedTab(
    icon: ImageVector?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (reduceMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Motion.SPATIAL_STIFFNESS_MEDIUM
        ),
        label = "tabScale"
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = motionTween(reduceMotion, Motion.DURATION_SHORT4),
        label = "tabContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionTween(reduceMotion, Motion.DURATION_SHORT4),
        label = "tabContent"
    )
    Surface(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick
            ),
        shape = shape,
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Corner radii (dp) for the connected tab row. Pure so it is unit tested. */
object ConnectedTabCorners {
    const val ACTIVE = 24f
    const val INACTIVE = 8f

    /** Clockwise from top-start: [topStart, topEnd, bottomEnd, bottomStart].
     *  The active tab is fully rounded on its outer edge; its inner edge (and
     *  the whole inactive tab) stays less rounded so the pair reads connected. */
    fun radii(index: Int, lastIndex: Int, active: Boolean): List<Float> {
        val outer = if (active) ACTIVE else INACTIVE
        return when (index) {
            0 -> listOf(outer, INACTIVE, INACTIVE, outer)
            lastIndex -> listOf(INACTIVE, outer, outer, INACTIVE)
            else -> listOf(INACTIVE, INACTIVE, INACTIVE, INACTIVE)
        }
    }
}

/** Active tab is fully rounded on its outer edge; the inner edge faces the
 *  neighbour and stays less rounded so the pair reads as connected. */
private fun connectedShape(index: Int, lastIndex: Int, active: Boolean): Shape {
    val (topStart, topEnd, bottomEnd, bottomStart) =
        ConnectedTabCorners.radii(index, lastIndex, active)
    return RoundedCornerShape(
        topStart = topStart.dp, topEnd = topEnd.dp,
        bottomEnd = bottomEnd.dp, bottomStart = bottomStart.dp
    )
}
