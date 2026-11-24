package dev.relay.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** One bar destination: its icon (and a filled one while chosen), its label for the tooltip, a dot for news. */
data class BarTab<T>(
    val value: T,
    val icon: ImageVector,
    val label: String,
    val selectedIcon: ImageVector = icon,
    val dot: Boolean = false,
)

private val BarHeight = 56.dp
private val PillWidth = 66.dp
private val PillHeight = 43.dp
private val TooltipGap = 12.dp
private const val TOOLTIP_HOLD_MS = 1300L

/**
 * Icon-only tabs. The chosen one sits in a `tool` pill that slides between them on the hero
 * spring; tapping or long-pressing a tab pops its label above it for a moment.
 */
@Composable
fun <T> BottomBar(
    tabs: List<BarTab<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = RelayTheme.colors
    val index = tabs.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val pill = remember { Animatable(index.toFloat()) }
    val heroSlide = RelayTheme.motion.hero.defaultSpatialSpec<Float>()
    LaunchedEffect(index) { pill.animateTo(index.toFloat(), heroSlide) }

    var labelled by remember { mutableIntStateOf(index) }
    var labelShown by remember { mutableStateOf(false) }
    var presses by remember { mutableIntStateOf(0) }
    LaunchedEffect(presses) {
        if (presses == 0) return@LaunchedEffect
        labelShown = true
        delay(TOOLTIP_HOLD_MS)
        labelShown = false
    }
    val showLabel = { i: Int ->
        labelled = i
        presses++
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(BarHeight)) {
        val slot = maxWidth / tabs.size
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(((slot * pill.value) + (slot - PillWidth) / 2).roundToPx(), 0) }
                .size(PillWidth, PillHeight)
                .background(c.tool, RelayTheme.shapes.circle),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            tabs.forEachIndexed { i, tab ->
                BarIcon(
                    tab,
                    chosen = i == index,
                    onClick = {
                        showLabel(i)
                        onSelect(tab.value)
                    },
                    onLongClick = { showLabel(i) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        AnimatedVisibility(
            labelShown,
            Modifier.above(centreX = slot * (labelled + 0.5f), bottom = (BarHeight - PillHeight) / 2 - TooltipGap),
            enter = scaleIn(RelayTheme.motion.hero.fastSpatialSpec(), 0.8f, TransformOrigin(0.5f, 1f)) +
                fadeIn(RelayTheme.motion.hero.fastEffectsSpec()),
            exit = fadeOut(tween(100)),
        ) { BarTooltip(tabs[labelled].label) }
    }
}

@Composable
private fun <T> BarIcon(tab: BarTab<T>, chosen: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val c = RelayTheme.colors
    val tint by animateColorAsState(if (chosen) c.ink else c.inkFaint, RelayTheme.motion.standard.fastEffectsSpec(), label = "bar-icon")
    Box(
        modifier
            .semantics {
                contentDescription = tab.label
                selected = chosen
            }
            .pressable(onClick, onLongClick = onLongClick, role = Role.Tab, haptic = Haptic.Selection, scale = false),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Icon(if (chosen) tab.selectedIcon else tab.icon, null, tint = tint, size = 22.dp)
            if (tab.dot) Box(
                Modifier.align(Alignment.TopEnd).offset(3.dp, (-2).dp).size(7.dp).background(c.info, RelayTheme.shapes.circle),
            )
        }
    }
}

/** Places the content with its bottom centre at ([centreX], [bottom]) of the parent, free to overflow above it. */
private fun Modifier.above(centreX: Dp, bottom: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) {
        p.place((centreX.toPx() - p.width / 2f).roundToInt(), (bottom.toPx() - p.height).roundToInt())
    }
}
