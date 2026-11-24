package dev.relay.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** One bar destination: its icon (and a filled one while chosen), its label, how the icon moves when picked, a dot for news. */
data class BarTab<T>(
    val value: T,
    val icon: ImageVector,
    val label: String,
    val selectedIcon: ImageVector = icon,
    val dot: Boolean = false,
    val move: BarMove = BarMove.Wiggle,
)

private val BarHeight = 56.dp
private val TabHeight = 48.dp
private val TabGap = 6.dp
private val RestCorner = 18.dp
private val IconSize = 22.dp
private val LabelGap = 8.dp
private val LabelSides = 18.dp
private val TooltipGap = 12.dp
private const val TOOLTIP_HOLD_MS = 1300L

/** How much a held tab swells into its neighbours. */
private const val PRESS_SWELL = 0.14f

/**
 * Tabs across the bar's whole width, so it lines up with what stands above it. The chosen one fills
 * with the accent, rounds off and grows its label on the hero spring while the others give way; a
 * held one swells. Without room for labels they stay icons, and a pick pops its label above.
 */
@Composable
fun <T> BottomBar(
    tabs: List<BarTab<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = tabs.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val hero = RelayTheme.motion.hero
    val chosen = tabs.mapIndexed { i, _ -> animateFloatAsState(if (i == index) 1f else 0f, hero.defaultSpatialSpec(), label = "bar-chosen") }
    val presses = tabs.map { remember { MutableInteractionSource() } }
    val held = presses.map { source ->
        val pressed by source.collectIsPressedAsState()
        animateFloatAsState(if (pressed) 1f else 0f, hero.fastSpatialSpec(), label = "bar-held")
    }

    var labelled by remember { mutableIntStateOf(index) }
    var labelShown by remember { mutableStateOf(false) }
    var asks by remember { mutableIntStateOf(0) }
    LaunchedEffect(asks) {
        if (asks == 0) return@LaunchedEffect
        labelShown = true
        delay(TOOLTIP_HOLD_MS)
        labelShown = false
    }
    val showLabel = { i: Int ->
        labelled = i
        asks++
    }

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = barLabelStyle()
    val labelWidths = remember(tabs.map { it.label }, labelStyle, density) {
        tabs.map { measurer.measure(it.label, labelStyle, maxLines = 1).size.width }
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(BarHeight), contentAlignment = Alignment.Center) {
        val full = constraints.maxWidth
        val gap = with(density) { TabGap.roundToPx() }
        val wide = with(density) {
            val content = labelWidths.max() + (IconSize + LabelGap + LabelSides * 2).roundToPx()
            labelledTab(full, gap, tabs.size, content, BAR_MIN_TAB_DP.dp.toPx())
        }
        val room = (full - gap * (tabs.size - 1)).toFloat()
        val rest = if (wide != null) (room - wide) / (tabs.size - 1).coerceAtLeast(1) else room / tabs.size
        val share = { i: Int -> lerp(rest, wide ?: rest, chosen[i].value) * (1f + PRESS_SWELL * held[i].value) }
        Layout(
            content = {
                tabs.forEachIndexed { i, tab ->
                    BarTabButton(
                        tab,
                        on = i == index,
                        chosen = chosen[i],
                        labelWidth = labelWidths[i].takeIf { wide != null },
                        interaction = presses[i],
                        onClick = {
                            if (wide == null) showLabel(i)
                            onSelect(tab.value)
                        },
                        onLongClick = { showLabel(i) },
                    )
                }
            },
            Modifier.fillMaxWidth().height(TabHeight),
        ) { measurables, constraints ->
            val widths = barWidths(constraints.maxWidth, gap, tabs.indices.map(share))
            val placed = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i].coerceAtLeast(0), constraints.maxHeight)) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                var x = 0
                placed.forEach {
                    it.placeRelative(x, 0)
                    x += it.width + gap
                }
            }
        }
        val settled = barWidths(full, gap, tabs.indices.map { if (it == index) wide ?: rest else rest })
        val centre = settled.take(labelled).sum() + gap * labelled + settled[labelled] / 2
        AnimatedVisibility(
            labelShown,
            Modifier.align(Alignment.TopStart).above(centre, with(density) { -TooltipGap.roundToPx() }),
            enter = scaleIn(hero.fastSpatialSpec(), 0.8f, TransformOrigin(0.5f, 1f)) + fadeIn(hero.fastEffectsSpec()),
            exit = fadeOut(tween(100)),
        ) { BarTooltip(tabs[labelled].label) }
    }
}

@Composable
private fun barLabelStyle() = RelayTheme.type.meta.copy(fontWeight = FontWeight.SemiBold)

/** Everything that moves is read while drawing: a pick doesn't recompose the tab. */
@Composable
private fun <T> BarTabButton(
    tab: BarTab<T>,
    on: Boolean,
    chosen: State<Float>,
    /** Null without room for labels. */
    labelWidth: Int?,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = RelayTheme.colors
    val move = remember { Animatable(1f) }
    var wasOn by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        val picked = on && !wasOn
        wasOn = on
        if (!picked) return@LaunchedEffect
        move.snapTo(0f)
        move.animateTo(1f, tween(BAR_MOVE_MS, easing = LinearEasing))
    }
    val shown = { chosen.value.coerceIn(0f, 1f) }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val corner = lerp(RestCorner.toPx(), size.height / 2, shown())
                drawRoundRect(lerp(c.container, c.accent, shown()), cornerRadius = CornerRadius(corner))
            }
            .semantics {
                contentDescription = tab.label
                selected = on
            }
            .pressable(onClick, onLongClick = onLongClick, role = Role.Tab, haptic = Haptic.Selection, scale = false, interaction = interaction)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                if (labelWidth != null) translationX = -(labelWidth + LabelGap.toPx()) / 2 * chosen.value
            },
        ) {
            Box(Modifier.graphicsLayer { play(tab.move, move.value) }) {
                Icon(tab.icon, null, Modifier.graphicsLayer { alpha = 1f - shown() }, tint = c.inkSoft, size = IconSize)
                Icon(tab.selectedIcon, null, Modifier.graphicsLayer { alpha = shown() }, tint = c.onAccent, size = IconSize)
            }
            if (tab.dot) Box(
                Modifier.align(Alignment.TopEnd).offset(3.dp, (-2).dp).size(7.dp).background(c.info, RelayTheme.shapes.circle),
            )
        }
        if (labelWidth != null) Text(
            tab.label,
            Modifier
                .wrapContentWidth(unbounded = true)
                .graphicsLayer {
                    // Beside the icon wherever that has got to, so the two never cross.
                    val toLabel = (IconSize.toPx() + labelWidth) / 2 + LabelGap.toPx()
                    translationX = toLabel - (labelWidth + LabelGap.toPx()) / 2 * chosen.value
                    // Only once the tab has room for it.
                    alpha = ((chosen.value - 0.45f) / 0.55f).coerceIn(0f, 1f)
                }
                .clearAndSetSemantics { },
            style = barLabelStyle(),
            color = c.onAccent,
            maxLines = 1,
        )
    }
}

/** Places the content with its bottom centre at ([centreX], [bottom]) of the parent, free to overflow above it. */
private fun Modifier.above(centreX: Int, bottom: Int) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { p.place(centreX - (p.width / 2f).roundToInt(), bottom - p.height) }
}
