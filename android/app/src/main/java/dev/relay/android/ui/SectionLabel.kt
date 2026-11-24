package dev.relay.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.ui.theme.RelayTheme

/**
 * A quiet label over a group of rows ("Pinned", "Recents"): 13sp in the faint ink, lined up
 * with [ListRow]'s text. [count] trails it in the same ink; [action] sits at the end.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    /** Where a long label is cut: paths read best cut from the start. */
    overflow: TextOverflow = TextOverflow.Ellipsis,
    action: (@Composable () -> Unit)? = null,
) {
    val c = RelayTheme.colors
    val style = RelayTheme.type.meta.copy(fontSize = 13.sp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 36.dp).padding(start = RowTextInset, end = RowTextInset, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, Modifier.weight(1f), style = style, color = c.inkSoft, maxLines = 1, overflow = overflow)
        if (count != null) Text(count, style = style, color = c.inkSoft)
        if (action != null) action()
    }
}
