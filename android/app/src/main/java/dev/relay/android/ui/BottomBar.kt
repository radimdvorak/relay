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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** One bar destination: its icon, the label it shows once chosen, what it's called in full, a dot for news. */
data class BarTab<T>(
    val value: T,
    val glyph: BarGlyph,
    val label: String,
    val name: String = label,
    val dot: Boolean = false,
)

private val BarHeight = 56.dp
private val PillHeight = 44.dp
/** How much the pill grows up and down while a finger holds it. */
private val PillLift = 2.dp
private val GlyphSize = 24.dp
private val LabelGap = 8.dp
private val LabelSides = 10.dp
private val TooltipGap = 12.dp
/** The share of the pill a tab has before its label starts to show. */
private const val LABEL_FROM = 0.7f
private const val TOOLTIP_HOLD_MS = 1300L

/**
 * Tabs in equal shares of the bar's whole width, so its ends line up with what stands above it. A
 * softly tinted pill slides to the chosen one on the hero spring and squashes against the bar's
 * ends instead of passing them; the icon takes colour, makes its move and is joined by its label.
 * Where a share is too narrow for that, a pick pops the label above. A finger sliding along the bar
 * takes the pill with it, waking each tab it passes; letting go picks the one it's on.
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
    /** The tab under the finger while it slides. */
    var under by remember { mutableStateOf<Int?>(null) }
    val sliding = under != null
    LaunchedEffect(index, sliding) { if (!sliding) pill.animateTo(index.toFloat(), heroSlide) }
    val lit = under ?: index
    val lift by animateFloatAsState(if (sliding) 1f else 0f, RelayTheme.motion.hero.fastSpatialSpec(), label = "bar-lift")
    val follow = RelayTheme.motion.standard.fastSpatialSpec<Float>()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

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
    val fill = barPillColor()
    BoxWithConstraints(modifier.fillMaxWidth().height(BarHeight), contentAlignment = Alignment.Center) {
        val slot = maxWidth / tabs.size
        val fits = with(density) { labelWidths.max().toDp() } + GlyphSize + LabelGap + LabelSides * 2 <= slot
        Row(
            Modifier.fillMaxWidth().height(PillHeight).barSlide(
                tabs.size,
                onSlide = { position ->
                    val over = barTabAt(position, tabs.size)
                    if (over != lit) {
                        haptics.perform(Haptic.Light)
                        if (!fits) showLabel(over)
                    }
                    under = over
                    scope.launch { pill.animateTo(position, follow) }
                },
                onDone = { picked ->
                    under = null
                    if (picked != null && picked != index) onSelect(tabs[picked].value)
                },
            ).drawBehind {
                val width = size.width / tabs.size
                val grown = PillLift.toPx() * lift
                val start = (pill.value * width).coerceAtLeast(0f)
                val end = ((pill.value + 1f) * width).coerceAtMost(size.width)
                val height = size.height + grown * 2
                drawRoundRect(fill, Offset(start, -grown), Size((end - start).coerceAtLeast(0f), height), CornerRadius(height / 2))
            },
        ) {
            tabs.forEachIndexed { i, tab ->
                BarTabButton(
                    tab,
                    on = i == lit,
                    chosen = { 1f - abs(pill.value.coerceIn(0f, tabs.lastIndex.toFloat()) - i).coerceAtMost(1f) },
                    current = i == index,
                    labelWidth = labelWidths[i].takeIf { fits },
                    onClick = {
                        if (!fits) showLabel(i)
                        onSelect(tab.value)
                    },
                    onLongClick = { showLabel(i) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        AnimatedVisibility(
            labelShown,
            Modifier.align(Alignment.TopStart).above(centreX = slot * (labelled + 0.5f), bottom = (BarHeight - PillHeight) / 2 - TooltipGap),
            enter = scaleIn(RelayTheme.motion.hero.fastSpatialSpec(), 0.8f, TransformOrigin(0.5f, 1f)) +
                fadeIn(RelayTheme.motion.hero.fastEffectsSpec()),
            exit = fadeOut(tween(100)),
        ) { BarTooltip(tabs[labelled].name) }
    }
}

@Composable
private fun barLabelStyle() = RelayTheme.type.meta.copy(fontWeight = FontWeight.SemiBold)

/** The composer's own colour, warmed by the accent. */
@Composable
private fun barPillColor() = RelayTheme.colors.let { lerp(it.raised, it.accent, 0.17f) }

/** Everything that moves is read while drawing: a pick doesn't recompose the tab on every frame. */
@Composable
private fun <T> BarTabButton(
    tab: BarTab<T>,
    /** Wearing the pill: the current tab, or the one a finger slides over. */
    on: Boolean,
    /** How much of the pill is on it: its colour and its label come and go with that. */
    chosen: () -> Float,
    current: Boolean,
    /** Null where there's no room for labels. */
    labelWidth: Int?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    val c = RelayTheme.colors
    val pill = barPillColor()
    val colors = remember(c, pill) {
        GlyphColors(line = lerp(c.inkFaint, c.inkSoft, 0.4f), ground = c.surface, accent = c.accent, soft = lerp(pill, c.accent, 0.42f))
    }
    val move = remember { Animatable(1f) }
    var wasOn by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        val picked = on && !wasOn
        wasOn = on
        if (!picked) return@LaunchedEffect
        move.snapTo(0f)
        move.animateTo(1f, tween(GLYPH_MOVE_MS, easing = LinearEasing))
    }
    Box(
        modifier
            .semantics {
                contentDescription = tab.name
                selected = current
            }
            .pressable(onClick, onLongClick = onLongClick, role = Role.Tab, haptic = Haptic.Selection, scale = false),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                if (labelWidth != null) translationX = -(labelWidth + LabelGap.toPx()) / 2 * chosen()
            },
        ) {
            BarGlyphIcon(tab.glyph, colors, chosen, { move.value }, size = GlyphSize)
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
                    translationX = (GlyphSize.toPx() + labelWidth) / 2 + LabelGap.toPx() - (labelWidth + LabelGap.toPx()) / 2 * chosen()
                    // Only once the pill is under all of it.
                    alpha = ((chosen() - LABEL_FROM) / (1f - LABEL_FROM)).coerceIn(0f, 1f)
                }
                .clearAndSetSemantics { },
            style = barLabelStyle(),
            color = c.ink,
            maxLines = 1,
        )
    }
}

/** Places the content with its bottom centre at ([centreX], [bottom]) of the parent, free to overflow above it. */
private fun Modifier.above(centreX: Dp, bottom: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) {
        p.place((centreX.toPx() - p.width / 2f).roundToInt(), (bottom.toPx() - p.height).roundToInt())
    }
}
