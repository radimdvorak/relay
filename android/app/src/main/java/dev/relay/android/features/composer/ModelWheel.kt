package dev.relay.android.features.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Haptic
import dev.relay.android.ui.pressable
import dev.relay.android.ui.rememberHaptics
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val RowHeight = 57.dp
private val PillHeight = 64.dp
private val WheelHeight = 238.dp

/** How far round the wheel one row turns, in radians: two rows away, text is half as tall. */
private const val TURN = 0.52f

/** Rows fade to nothing this many rows from the middle. */
private const val VANISH = 2.7f

/**
 * Names on a wheel under a fixed pill. Scrolling snaps a row into the pill and picks it, a tap on
 * a row rolls it there. [trailing] is the picked row's end, which stays put while names roll by.
 * [onCentre] hears each row as it passes the middle, before it's picked.
 */
@Composable
internal fun ModelWheel(
    names: List<WheelRow>,
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCentre: (String) -> Unit = {},
    trailing: @Composable (id: String) -> Unit = {},
) {
    val c = RelayTheme.colors
    val haptics = rememberHaptics()
    val at = names.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = at)
    val centre by remember(list) { derivedStateOf { list.centredIndex() } }
    // Rolling to a pick of ours, which mustn't be taken for a pick of theirs on the way.
    var following by remember { mutableStateOf(false) }
    val rows by rememberUpdatedState(names)
    val chosen by rememberUpdatedState(selected)
    val pick by rememberUpdatedState(onPick)
    val passed by rememberUpdatedState(onCentre)

    var shown by remember { mutableStateOf(names.map { it.id }) }
    LaunchedEffect(names, selected) {
        val ids = names.map { it.id }
        val sameRows = ids == shown
        shown = ids
        if (list.firstVisibleItemIndex == at && list.firstVisibleItemScrollOffset == 0 && sameRows) return@LaunchedEffect
        following = true
        try {
            if (sameRows) list.animateScrollToItem(at) else list.scrollToItem(at)
        } finally {
            following = false
        }
    }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.distinctUntilChanged().drop(1).filterNot { it || following }.collect {
            rows.getOrNull(list.centredIndex())?.let { if (it.id != chosen) pick(it.id) }
        }
    }
    LaunchedEffect(list) {
        snapshotFlow { list.centredIndex() }.distinctUntilChanged().drop(1).filter { list.isScrollInProgress }.collect {
            if (!following) haptics.perform(Haptic.Selection)
        }
    }
    LaunchedEffect(list) {
        snapshotFlow { rows.getOrNull(list.centredIndex())?.id }.distinctUntilChanged().collect { if (it != null) passed(it) }
    }

    Box(modifier.fillMaxWidth().height(WheelHeight), contentAlignment = Alignment.Center) {
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(PillHeight).background(c.tool, RelayTheme.shapes.circle))
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxWidth().height(WheelHeight),
            contentPadding = PaddingValues(vertical = (WheelHeight - RowHeight) / 2),
            flingBehavior = rememberSnapFlingBehavior(list, SnapPosition.Center),
        ) {
            itemsIndexed(names, key = { _, row -> row.id }) { i, row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .pressable(onClick = { if (row.id != selected) pick(row.id) }, role = Role.RadioButton, scale = false)
                        .semantics { this.selected = row.id == selected }
                        .padding(horizontal = 38.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        row.name,
                        Modifier.weight(1f).graphicsLayer { roll(list.rowsFromCentre(i)) },
                        style = RelayTheme.type.row.copy(fontWeight = FontWeight.Medium),
                        color = ColorProducer { lerp(c.ink, c.inkSoft, abs(list.rowsFromCentre(i)).coerceIn(0f, 1f)) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (i == centre) Box(Modifier.graphicsLayer { translationY = -list.rowsFromCentre(i) * RowHeight.toPx() }) {
                        trailing(row.id)
                    }
                }
            }
        }
    }
}

internal data class WheelRow(val id: String, val name: String)

/** A row turned away by [away] rows: squashed, drawn in toward the middle, and faded at the edge. */
private fun GraphicsLayerScope.roll(away: Float) {
    val angle = (away * TURN).coerceIn(-1.5f, 1.5f)
    transformOrigin = TransformOrigin(0f, 0.5f)
    scaleY = cos(angle)
    translationY = (sin(angle) / TURN - away) * RowHeight.toPx()
    alpha = ((VANISH - abs(away)) / (VANISH - 1f)).coerceIn(0f, 1f)
}

/** How many rows below the middle row [index] is; far away when it isn't laid out. */
private fun LazyListState.rowsFromCentre(index: Int): Float {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return VANISH
    val middle = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    return (item.offset + item.size / 2f - middle) / item.size
}

private fun LazyListState.centredIndex(): Int {
    val middle = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    return layoutInfo.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - middle) }?.index ?: firstVisibleItemIndex
}
