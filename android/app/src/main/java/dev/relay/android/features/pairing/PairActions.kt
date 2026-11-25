package dev.relay.android.features.pairing

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.ContentColor
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.pressedFill
import dev.relay.android.ui.theme.RelayTheme

private val ActionHeight = 56.dp
/** Half the height, not a percentage: the seam's corners animate up to it. */
private val RoundCorner = ActionHeight / 2
private val SeamCorner = 8.dp
private val PressedSeamCorner = 20.dp
/** How much of its neighbour's room a pressed half takes. */
private const val PRESS_SWELL = 0.12f
/** Above this text size the two labels no longer fit beside each other. */
private const val STACK_FONT_SCALE = 1.3f

internal class PairAction(val text: String, val icon: ImageVector?, val onClick: () -> Unit)

/** One action across the column. */
@Composable
internal fun PairAction(action: PairAction, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    ActionSurface(action, interaction, strong = true, RelayTheme.shapes.circle, modifier.fillMaxWidth())
}

/**
 * Two halves of one pill, the first the one to take: Material's connected button group. A pressed
 * half rounds at the seam and swells into its neighbour.
 */
@Composable
internal fun PairActions(first: PairAction, second: PairAction) {
    val firstTouch = remember { MutableInteractionSource() }
    val secondTouch = remember { MutableInteractionSource() }
    if (LocalDensity.current.fontScale > STACK_FONT_SCALE) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionSurface(first, firstTouch, strong = true, RelayTheme.shapes.circle, Modifier.fillMaxWidth())
            ActionSurface(second, secondTouch, strong = false, RelayTheme.shapes.circle, Modifier.fillMaxWidth())
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val firstSeam = seamCorner(firstTouch)
        val secondSeam = seamCorner(secondTouch)
        ActionSurface(
            first, firstTouch, strong = true,
            RoundedCornerShape(RoundCorner, firstSeam, firstSeam, RoundCorner),
            Modifier.weight(1.15f + swell(firstTouch)),
        )
        ActionSurface(
            second, secondTouch, strong = false,
            RoundedCornerShape(secondSeam, RoundCorner, RoundCorner, secondSeam),
            Modifier.weight(1f + swell(secondTouch)),
        )
    }
}

@Composable
private fun seamCorner(touch: MutableInteractionSource): Dp {
    val pressed by touch.collectIsPressedAsState()
    val corner by animateDpAsState(
        if (pressed) PressedSeamCorner else SeamCorner,
        RelayTheme.motion.hero.fastSpatialSpec(),
        label = "pair-seam",
    )
    // The spring overshoots below zero on its way back; a corner can't.
    return corner.coerceAtLeast(0.dp)
}

@Composable
private fun swell(touch: MutableInteractionSource): Float {
    val pressed by touch.collectIsPressedAsState()
    val swell by animateFloatAsState(
        if (pressed) PRESS_SWELL else 0f,
        RelayTheme.motion.hero.fastSpatialSpec(),
        label = "pair-swell",
    )
    return swell
}

@Composable
private fun ActionSurface(
    action: PairAction,
    touch: MutableInteractionSource,
    strong: Boolean,
    shape: Shape,
    modifier: Modifier,
) {
    val c = RelayTheme.colors
    val pressed by touch.collectIsPressedAsState()
    val content = if (strong) c.onStrong else c.ink
    Box(
        modifier
            .pressable(action.onClick, scale = false, interaction = touch)
            .clip(shape)
            .background(actionFill(strong, pressed))
            .heightIn(min = ActionHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ContentColor(content) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (action.icon != null) Icon(action.icon, null, size = 20.dp)
                Text(
                    action.text,
                    style = RelayTheme.type.body.copy(fontWeight = FontWeight.Medium),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun actionFill(strong: Boolean, pressed: Boolean): Color {
    val c = RelayTheme.colors
    if (strong) return if (pressed) c.strong.copy(alpha = 0.85f) else c.strong
    return if (pressed) pressedFill(c.tool, c) else c.tool
}
