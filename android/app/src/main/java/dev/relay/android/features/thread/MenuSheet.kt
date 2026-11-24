package dev.relay.android.features.thread

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Icon
import dev.relay.android.ui.ListGroup
import dev.relay.android.ui.ListRow
import dev.relay.android.ui.RowSize
import dev.relay.android.ui.Sheet
import dev.relay.android.ui.theme.RelayTheme

data class MenuItem(
    val label: String,
    val hint: String? = null,
    val destructive: Boolean = false,
    val icon: ImageVector? = null,
    val onPress: () -> Unit,
)

/** A list of actions in a sheet; picking one closes it first. */
@Composable
fun MenuSheet(items: List<MenuItem>, onClose: () -> Unit, title: String? = null, description: String? = null) {
    Sheet(onDismiss = onClose, title = title, description = description) {
        MenuRows(items, onClose)
    }
}

@Composable
internal fun MenuRows(items: List<MenuItem>, onClose: () -> Unit) {
    val c = RelayTheme.colors
    // Rows without an icon among rows with one keep their words in line.
    val iconed = items.any { it.icon != null }
    ListGroup(Modifier.sheetBleed(12.dp)) {
        for (item in items) {
            val ink = if (item.destructive) c.danger else c.ink
            ListRow(
                item.label,
                subtitle = item.hint,
                leading = if (iconed) ({ item.icon?.let { Icon(it, null, tint = if (item.destructive) c.danger else c.inkSoft, size = 20.dp) } }) else null,
                size = if (item.hint == null) RowSize.Sm else RowSize.Default,
                titleColor = ink,
                onClick = {
                    onClose()
                    item.onPress()
                },
            )
        }
    }
}

/** Reaches [by] past a sheet's side padding, for rows and groups that carry their own. */
internal fun Modifier.sheetBleed(by: Dp = 8.dp) = layout { measurable, constraints ->
    val more = by.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + more, maxWidth = constraints.maxWidth + more))
    layout(placeable.width - more, placeable.height) { placeable.place(-by.roundToPx(), 0) }
}
