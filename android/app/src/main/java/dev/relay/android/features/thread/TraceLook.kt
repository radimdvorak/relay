package dev.relay.android.features.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.ui.Icon
import dev.relay.android.ui.Text
import dev.relay.android.ui.theme.RelayTheme
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight

// How an agent's work looks between its words: small soft rows, an icon and a line of text each,
// all the same height so calls coming and going don't move the thread.

/** The height every row keeps. */
internal val TraceLine = 30.dp
internal val TraceIcon = 16.dp
internal val TraceGap = 8.dp

/** Where a row's words start, and what opens under it. */
internal val TraceIndent = TraceIcon + TraceGap

@Composable
@ReadOnlyComposable
internal fun traceText(): TextStyle = RelayTheme.type.meta

/** Code in a row: a file's name, a pattern, a program. */
@Composable
@ReadOnlyComposable
internal fun traceCode(): TextStyle = RelayTheme.type.codeSmall.copy(fontSize = 12.sp, lineHeight = 18.sp)

/** What opened code and output sit in, inside a row. */
internal val TraceBox get() = RelayTheme.shapes.row

@Composable
internal fun Step(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = TraceLine),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TraceGap),
        content = content,
    )
}

@Composable
internal fun StepIcon(icon: ImageVector, tint: Color = RelayTheme.colors.inkFaint) {
    Icon(icon, null, tint = tint, size = TraceIcon)
}

@Composable
internal fun StepText(text: String, modifier: Modifier = Modifier, color: Color = RelayTheme.colors.inkSoft, style: TextStyle = traceText()) {
    Text(text, modifier, style = style, color = color, maxLines = 1)
}

/** Points down once what it heads is open. */
@Composable
internal fun StepChevron(open: Boolean) {
    Icon(
        TablerIcons.Outline.ChevronRight, null, if (open) Modifier.rotate(90f) else Modifier,
        tint = RelayTheme.colors.inkFaint, size = 14.dp,
    )
}
