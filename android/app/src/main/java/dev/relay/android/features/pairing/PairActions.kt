package dev.relay.android.features.pairing

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.rememberTextMeasurer
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
/** How much wider than its half a pressed one grows, out of its neighbour. */
private const val PRESS_SWELL = 0.12f
private val ActionPadding = 16.dp
private val ActionIcon = 20.dp
private val IconGap = 8.dp
private val SeamGap = 3.dp

internal class PairAction(val text: String, val icon: ImageVector?, val onClick: () -> Unit)

/** One action across the column. */
@Composable
internal fun PairAction(action: PairAction, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    ActionSurface(action, interaction, strong = true, RelayTheme.shapes.circle, modifier.fillMaxWidth())
}

/**
 * Two equal halves of one pill, the first the one to take: Material's connected button group. A
 * pressed half rounds at the seam and swells into its neighbour. Stacked when a label needs more
 * than its half.
 */
@Composable
internal fun PairActions(first: PairAction, second: PairAction) {
    val firstTouch = remember { MutableInteractionSource() }
    val secondTouch = remember { MutableInteractionSource() }
    val widest = maxOf(actionWidth(first), actionWidth(second))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // What's left of a half while its neighbour is pressed.
        val narrowestHalf = (maxWidth - SeamGap) / (2f + PRESS_SWELL)
        if (widest <= narrowestHalf) Halves(first, firstTouch, second, secondTouch)
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionSurface(first, firstTouch, strong = true, RelayTheme.shapes.circle, Modifier.fillMaxWidth())
            ActionSurface(second, secondTouch, strong = false, RelayTheme.shapes.circle, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Halves(
    first: PairAction,
    firstTouch: MutableInteractionSource,
    second: PairAction,
    secondTouch: MutableInteractionSource,
) {
    val firstSeam = seamCorner(firstTouch)
    val secondSeam = seamCorner(secondTouch)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SeamGap)) {
        ActionSurface(
            first, firstTouch, strong = true,
            RoundedCornerShape(RoundCorner, firstSeam, firstSeam, RoundCorner),
            Modifier.weight(1f + swell(firstTouch)),
        )
        ActionSurface(
            second, secondTouch, strong = false,
            RoundedCornerShape(secondSeam, RoundCorner, RoundCorner, secondSeam),
            Modifier.weight(1f + swell(secondTouch)),
        )
    }
}

@Composable
private fun actionStyle() = RelayTheme.type.body.copy(fontWeight = FontWeight.Medium)

/** The width an action needs to show its whole label. */
@Composable
private fun actionWidth(action: PairAction): Dp {
    val label = rememberTextMeasurer().measure(action.text, actionStyle(), maxLines = 1).size.width
    val icon = if (action.icon == null) 0.dp else ActionIcon + IconGap
    return with(LocalDensity.current) { label.toDp() } + icon + ActionPadding * 2
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
            .padding(horizontal = ActionPadding, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ContentColor(content) {
            Row(horizontalArrangement = Arrangement.spacedBy(IconGap), verticalAlignment = Alignment.CenterVertically) {
                if (action.icon != null) Icon(action.icon, null, size = ActionIcon)
                Text(
                    action.text,
                    style = actionStyle(),
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
