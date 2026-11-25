package dev.relay.android.features.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.fadesCutSide
import dev.relay.android.ui.theme.RelayTheme

private val RowGap = 2.dp
private val CellTop = 8.dp

/** A cell's sides, and what they give up before its text wraps. */
private val RoomySide = 12.dp
private val TightSide = 10.dp

/** A column of prose wraps down to here, and one long word no further than it can hold. */
private val NarrowestProse = 72.dp
private val LongestWord = 140.dp

/** How wide prose gets in a table that scrolls anyway. */
private val WidestScrolled = 200.dp

/**
 * A table as rows of tiles, the gaps between them in place of lines. Its columns share the width
 * there is; it scrolls sideways only when they can't be read at that.
 */
@Composable
internal fun Table(table: MdBlock.Table, ctx: MdContext) {
    val c = RelayTheme.colors
    val style = RelayTheme.type.meta.copy(lineHeight = RelayTheme.type.code.lineHeight)
    val headerStyle = style.copy(fontWeight = FontWeight.Medium)
    val rows = remember(table) { listOf(table.header) + table.rows }
    val columns = remember(rows) { rows.maxOf { it.size } }
    if (columns == 0) return
    val scroll = rememberScrollState()
    BoxWithConstraints {
        Layout(
            modifier = Modifier.clip(RelayTheme.shapes.field).fadesCutSide(scroll, c.surface).horizontalScroll(scroll),
            content = {
                rows.forEachIndexed { r, _ -> Box(Modifier.background(if (r == 0) c.tool else c.container)) }
                rows.forEachIndexed { r, cells ->
                    for (column in 0 until columns) Inlines(
                        cells.getOrElse(column) { emptyList() },
                        ctx,
                        if (r == 0) headerStyle else style,
                        // A table scrolls sideways; its cells select together, in Compose.
                        native = false,
                    )
                }
            },
            measurePolicy = tablePolicy(rows.size, columns, constraints.maxWidth),
        )
    }
}

/** Lays out each row's tile, then every row's cells; the columns share [available] pixels. */
private fun tablePolicy(rows: Int, columns: Int, available: Int) = MeasurePolicy { measurables, _ ->
    val tiles = measurables.take(rows)
    val cells = measurables.drop(rows).chunked(columns)
    val column = { i: Int -> cells.map { it[i] } }
    val texts = List(columns) { columnText(column(it), NarrowestProse.roundToPx(), LongestWord.roundToPx()) }
    val fitted = fitColumns(texts, available, RoomySide.roundToPx(), TightSide.roundToPx(), WidestScrolled.roundToPx())

    val placed = cells.map { row -> row.mapIndexed { i, cell -> cell.measure(Constraints.fixedWidth(fitted.texts[i])) } }
    val heights = placed.map { row -> row.maxOf { it.height } + 2 * CellTop.roundToPx() }
    val width = fitted.texts.sum() + columns * 2 * fitted.side
    val backs = tiles.mapIndexed { r, tile -> tile.measure(Constraints.fixed(width, heights[r])) }

    layout(width, heights.sum() + (rows - 1) * RowGap.roundToPx()) {
        var y = 0
        placed.forEachIndexed { r, row ->
            backs[r].place(0, y)
            var x = fitted.side
            row.forEachIndexed { i, cell ->
                cell.place(x, y + CellTop.roundToPx())
                x += fitted.texts[i] + 2 * fitted.side
            }
            y += heights[r] + RowGap.roundToPx()
        }
    }
}

private fun columnText(cells: List<Measurable>, narrowestProse: Int, longestWord: Int): ColumnText {
    val natural = cells.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
    val word = cells.maxOf { it.minIntrinsicWidth(Constraints.Infinity) }.coerceAtMost(longestWord)
    return ColumnText(natural, least = maxOf(word, narrowestProse).coerceAtMost(natural))
}

/** A column's text on one line each, and the narrowest it still reads well at. */
internal data class ColumnText(val natural: Int, val least: Int)

/** Each column's text width, and the space at a cell's sides. */
internal data class FittedColumns(val texts: List<Int>, val side: Int)

/** Every column at its natural width if they fit, else the wide ones wrapped, else too wide for [available]. */
internal fun fitColumns(columns: List<ColumnText>, available: Int, roomySide: Int, tightSide: Int, widestScrolled: Int): FittedColumns {
    val room = { side: Int -> available - columns.size * 2 * side }
    val natural = columns.map { it.natural }
    if (natural.sum() <= room(roomySide)) return FittedColumns(natural, roomySide)
    if (natural.sum() <= room(tightSide)) return FittedColumns(natural, tightSide)
    if (columns.sumOf { it.least } <= room(tightSide)) return FittedColumns(shareRoom(columns, room(tightSide)), tightSide)
    return FittedColumns(columns.map { maxOf(it.least, minOf(it.natural, widestScrolled)) }, roomySide)
}

/** Narrow columns keep their natural width; the wide ones come down to one width, the widest that fits. */
private fun shareRoom(columns: List<ColumnText>, room: Int): List<Int> {
    fun at(cap: Int) = columns.map { minOf(it.natural, maxOf(it.least, cap)) }
    var fits = 0
    var over = room + 1
    while (over - fits > 1) {
        val cap = (fits + over) / 2
        if (at(cap).sum() <= room) fits = cap else over = cap
    }
    return at(fits)
}
