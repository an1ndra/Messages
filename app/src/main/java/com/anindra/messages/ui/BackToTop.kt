package com.anindra.messages.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anindra.messages.R
import com.anindra.messages.ui.theme.Motion
import com.anindra.messages.ui.theme.motionTween

/**
 * When the inbox offers to take you back to the top.
 *
 * A list you have scrolled a screen or two into is one flick from the top, and
 * the button would sit on top of the conversations for nothing. Past this many
 * rows the flick is a chore and the button earns its place.
 */
internal object BackToTop {

    /** Rows that have to be out of sight before the button appears. */
    const val THRESHOLD = 8

    /** How solid the button is drawn. It floats over the rows in the middle of
     *  the screen, so a fully opaque disc would read as a hole punched through
     *  the list; at 70% the conversation behind it stays legible. */
    const val ALPHA = 0.7f

    /** The disc the button is drawn on. */
    val SIZE = 40.dp

    /** The edge the button enters from. Diagnostics prints this same value. */
    const val ENTRY_DESCRIPTION = "from bottom"

    fun visible(firstVisibleIndex: Int): Boolean = firstVisibleIndex >= THRESHOLD

    /** A positive offset starts below the screen, so the button rises into place. */
    fun enter(reduceMotion: Boolean): EnterTransition =
        fadeIn(motionTween<Float>(reduceMotion, Motion.DURATION_SHORT4)) +
            slideInVertically(motionTween<IntOffset>(reduceMotion, Motion.DURATION_SHORT4)) { it }

    fun exit(reduceMotion: Boolean): ExitTransition =
        fadeOut(motionTween<Float>(reduceMotion, Motion.DURATION_SHORT4)) +
            slideOutVertically(motionTween<IntOffset>(reduceMotion, Motion.DURATION_SHORT4)) { it }
}

/**
 * "Back to top", floating in the middle of the bottom edge.
 *
 * A Surface rather than a floating-action-button: the FAB's container adds its
 * own corner shape and minimum touch target around the content, which drew a
 * square edge around a round button. This one is the circle it looks like.
 */
@Composable
internal fun BackToTopFab(onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(BackToTop.SIZE)
            .alpha(BackToTop.ALPHA)
            .semantics { contentDescription = context.getString(R.string.conversations_back_to_top) }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
