package dev.relay.android.features.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.remote.model.DiffLine
import dev.relay.android.remote.model.RemoteDiff
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme

/** A diff's lines, wrapped: one column, or [split] side by side. */
@Composable
internal fun DiffBody(diff: RemoteDiff, split: Boolean, bottom: Dp) {
    val unified = remember(diff) { unifiedRows(diff) }
    val pairs = remember(diff, split) { if (split) splitRows(diff) else emptyList() }
    SelectionContainer {
        // The top padding keeps the first header's fill clear of the sheet's round corners.
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = bottom)) {
            if (split) itemsIndexed(pairs, contentType = { _, r -> r::class }) { _, r ->
                when (r) {
                    is SplitRow.Hunk -> HunkHeader(r.header)
                    is SplitRow.Pair -> SplitLine(r)
                }
            } else itemsIndexed(unified, contentType = { _, r -> r::class }) { _, r ->
                when (r) {
                    is DiffRow.Hunk -> HunkHeader(r.header)
                    is DiffRow.Line -> UnifiedLine(r.line)
                }
            }
            if (diff.truncated) item { EmptyNote("The rest of this diff is on your computer.", Modifier.fillMaxWidth()) }
        }
    }
}

private val gutterStyle @Composable get() = RelayTheme.type.codeSmall.copy(lineHeight = 18.sp)
private val codeStyle @Composable get() = RelayTheme.type.code.copy(fontSize = 12.sp, lineHeight = 18.sp)

@Composable
private fun HunkHeader(header: String) {
    Text(
        header,
        Modifier.fillMaxWidth().background(RelayTheme.colors.container).padding(horizontal = 16.dp, vertical = 5.dp),
        style = RelayTheme.type.codeSmall,
        color = RelayTheme.colors.inkSoft,
    )
}

@Composable
private fun lineFill(kind: String): Color = when (kind) {
    "add" -> RelayTheme.colors.addedBg
    "del" -> RelayTheme.colors.removedBg
    else -> Color.Transparent
}

/** The + or − beside a line: the diff's colour pulled toward the text, so it reads on its own fill in any palette. */
@Composable
private fun signColor(kind: String): Color {
    val c = RelayTheme.colors
    return when (kind) {
        "add" -> lerp(c.added, c.ink, 0.35f)
        "del" -> lerp(c.removed, c.ink, 0.2f)
        else -> c.inkFaint
    }
}

@Composable
private fun UnifiedLine(line: DiffLine) {
    Row(Modifier.fillMaxWidth().background(lineFill(line.kind)).padding(end = 12.dp)) {
        Gutter(if (line.kind == "del") line.old else line.new)
        Text(
            when (line.kind) {
                "add" -> "+"
                "del" -> "−"
                else -> " "
            },
            Modifier.width(18.dp),
            style = codeStyle,
            color = signColor(line.kind),
            textAlign = TextAlign.Center,
        )
        Text(line.text.ifEmpty { " " }, Modifier.weight(1f), style = codeStyle)
    }
}

@Composable
private fun Gutter(number: Int?) {
    Text(
        number?.toString() ?: "",
        Modifier.width(42.dp),
        style = gutterStyle,
        color = RelayTheme.colors.inkSoft,
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

@Composable
private fun SplitLine(row: SplitRow.Pair) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Half(row.left, row.left?.old, Modifier.weight(1f))
        // The surface showing through tells the halves apart.
        Spacer(Modifier.width(2.dp))
        Half(row.right, row.right?.new, Modifier.weight(1f))
    }
}

@Composable
private fun Half(line: DiffLine?, number: Int?, modifier: Modifier) {
    val fill = if (line == null) RelayTheme.colors.container else lineFill(line.kind)
    Row(modifier.fillMaxHeight().background(fill).padding(end = 8.dp)) {
        Gutter(number)
        Text(line?.text?.ifEmpty { " " } ?: "", Modifier.weight(1f).padding(start = 8.dp), style = codeStyle)
    }
}
