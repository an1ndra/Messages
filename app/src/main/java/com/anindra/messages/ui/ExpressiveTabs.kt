package com.anindra.messages.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A single option in an [ExpressiveTabs] row. */
data class ExpressiveTab(val labelRes: Int, val icon: ImageVector)

/**
 * M3 Expressive single-select tab row built on a connected [ButtonGroup]: the
 * active tab is a fully rounded pill while the neighbours keep their inner
 * corner, and the pressed item expands as its neighbour compresses. Shared by
 * Spam & blocked and Trash so both folders look and behave identically.
 */
@Composable
fun ExpressiveTabs(
    tabs: List<ExpressiveTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = tabs.map { stringResource(it.labelRes) }
    val interactions = remember { List(tabs.size) { MutableInteractionSource() } }
    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        expandedRatio = 1f,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        tabs.forEachIndexed { index, tab ->
            val icon = tab.icon
            customItem(
                buttonGroupContent = {
                    val contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    val layoutDirection = LocalLayoutDirection.current
                    ToggleButton(
                        checked = selected == index,
                        onCheckedChange = { onSelect(index) },
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(
                                interactionSource = interactions[index],
                                compressionLimit = contentPadding.calculateEndPadding(layoutDirection)
                            )
                            .semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        contentPadding = contentPadding,
                        interactionSource = interactions[index]
                    ) {
                        Icon(icon, contentDescription = null)
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                        Text(
                            text = labels[index],
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Visible
                        )
                    }
                },
                menuContent = {
                    DropdownMenuItem(
                        leadingIcon = { Icon(icon, contentDescription = null) },
                        text = { Text(labels[index]) },
                        onClick = { onSelect(index) }
                    )
                }
            )
        }
    }
}
