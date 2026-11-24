package dev.relay.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme

data class TabOption<T>(val value: T, val label: String)

private class Slots {
    val x = mutableStateMapOf<Int, Dp>()
    val w = mutableStateMapOf<Int, Dp>()
}

/** The indicator's x and width: it jumps into place the first time, then slides. */
@Composable
private fun indicator(slots: Slots, index: Int): Pair<Dp, Dp> {
    var settled by remember { mutableStateOf(false) }
    val placed = slots.w.isNotEmpty()
    LaunchedEffect(placed) {
        if (placed) {
            withFrameNanos { }
            settled = true
        }
    }
    val spec: AnimationSpec<Dp> = if (settled) MaterialTheme.motionScheme.defaultSpatialSpec() else snap()
    val x by animateDpAsState(slots.x[index] ?: 0.dp, spec, label = "tab-x")
    val w by animateDpAsState(slots.w[index] ?: 0.dp, spec, label = "tab-w")
    return x to w
}

/**
 * Segmented tabs: a round tool track with a raised pill that slides to the chosen one. `fill`
 * spreads the tabs across the width; otherwise they hug their labels and scroll when they don't fit.
 */
@Composable
fun <T> SegmentedTabs(
    options: List<TabOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    /** The chosen tab wears the send colour instead of the raised pill: for a filter that changes the whole list. */
    primary: Boolean = false,
) {
    val c = RelayTheme.colors
    val slots = remember { Slots() }
    val density = LocalDensity.current
    val index = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val (x, w) = indicator(slots, index)
    val track = RelayTheme.shapes.circle
    val pill = RelayTheme.shapes.circle
    Box(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(track)
            .background(c.tool)
            .padding(3.dp)
            .height(34.dp),
    ) {
        Box((if (fill) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState())).fillMaxHeight()) {
        if (w > 0.dp) Box(
            Modifier.offset(x = x).width(w).fillMaxHeight().then(
                if (primary) Modifier.background(c.send, pill)
                else Modifier.background(if (c.isDark) lerp(c.tool, c.ink, 0.14f) else c.raised, pill),
            ),
        )
        Row(
            (if (fill) Modifier.fillMaxWidth() else Modifier).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { i, o ->
                val on = i == index
                val chosenInk = if (primary) c.onSend else c.ink
                val color by animateColorAsState(if (on) chosenInk else c.inkSoft, MaterialTheme.motionScheme.fastEffectsSpec(), label = "seg-color")
                Box(
                    Modifier
                        .then(if (fill) Modifier.weight(1f) else Modifier)
                        .fillMaxHeight()
                        .onPlaced {
                            with(density) {
                                slots.x[i] = it.positionInParent().x.toDp()
                                slots.w[i] = it.size.width.toDp()
                            }
                        }
                        .semantics { this.selected = on }
                        .pressable({ onSelect(o.value) }, role = Role.Tab, scale = false, haptic = Haptic.Selection)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(o.label, style = tabLabel(), color = color, maxLines = 1) }
            }
        }
        }
    }
}

/** Line tabs: labels with a 2dp ink bar sliding under the chosen one; no rule under the row. */
@Composable
fun <T> LineTabs(
    options: List<TabOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = RelayTheme.colors
    val slots = remember { Slots() }
    val density = LocalDensity.current
    val index = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val (x, w) = indicator(slots, index)
    Box(modifier.fillMaxWidth().height(40.dp)) {
        Box(Modifier.horizontalScroll(rememberScrollState()).fillMaxHeight()) {
        Row(Modifier.fillMaxHeight()) {
            options.forEachIndexed { i, o ->
                val on = i == index
                Box(
                    Modifier
                        .fillMaxHeight()
                        .onPlaced {
                            with(density) {
                                slots.x[i] = it.positionInParent().x.toDp()
                                slots.w[i] = it.size.width.toDp()
                            }
                        }
                        .semantics { this.selected = on }
                        .pressable({ onSelect(o.value) }, role = Role.Tab, scale = false, haptic = Haptic.Selection)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(o.label, style = tabLabel(), color = if (on) c.ink else c.inkSoft, maxLines = 1)
                }
            }
        }
        if (w > 0.dp) Box(
            Modifier.align(Alignment.BottomStart).offset(x = x).width(w).height(2.dp).background(c.ink, RelayTheme.shapes.circle),
        )
        }
    }
}

@Composable
private fun tabLabel() = RelayTheme.type.meta.copy(fontWeight = FontWeight.Medium)
