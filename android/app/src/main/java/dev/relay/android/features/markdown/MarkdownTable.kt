package dev.relay.android.features.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme

private const val NARROWEST = 64
private const val WIDEST = 200

/** Roughly what a character of cell text takes, and a cell's own sides. */
private const val PER_CHARACTER = 7.5f
private const val CELL_SIDES = 28

/** A table as rows of tiles, the gaps between them in place of lines; it scrolls sideways when it's wide. */
@Composable
internal fun Table(table: MdBlock.Table, ctx: MdContext) {
    val c = RelayTheme.colors
    val cell = RelayTheme.type.meta.copy(lineHeight = RelayTheme.type.code.lineHeight)
    val rows = remember(table) { listOf(table.header) + table.rows }
    val widths = remember(rows) { columnWidths(rows) }
    Column(
        Modifier.horizontalScroll(rememberScrollState()).clip(RelayTheme.shapes.field),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEachIndexed { r, cells ->
            Row(Modifier.background(if (r == 0) c.tool else c.container)) {
                cells.forEachIndexed { i, inlines ->
                    Inlines(
                        inlines,
                        ctx,
                        if (r == 0) cell.copy(fontWeight = FontWeight.Medium) else cell,
                        Modifier.width((widths.getOrNull(i) ?: NARROWEST).dp).padding(horizontal = 12.dp, vertical = 8.dp),
                        // A table scrolls sideways; its cells select together, in Compose.
                        native = false,
                    )
                }
            }
        }
    }
}

/** Each column as wide as its longest cell needs, within reason: a number's column stays narrow, prose wraps. */
internal fun columnWidths(rows: List<List<List<MdInline>>>): List<Int> =
    List(rows.maxOfOrNull { it.size } ?: 0) { column ->
        val longest = rows.maxOf { row -> row.getOrNull(column)?.let(::textLength) ?: 0 }
        (longest * PER_CHARACTER + CELL_SIDES).toInt().coerceIn(NARROWEST, WIDEST)
    }

private fun textLength(inlines: List<MdInline>): Int = inlines.sumOf { inline ->
    when (inline) {
        is MdInline.Plain -> inline.text.length
        is MdInline.Strong -> textLength(inline.children)
        is MdInline.Em -> textLength(inline.children)
        is MdInline.Del -> textLength(inline.children)
        is MdInline.Link -> textLength(inline.children)
        is MdInline.Code -> inline.code.length + 1
        is MdInline.Math -> inline.tex.length
        is MdInline.Image -> inline.alt.length
    }
}
