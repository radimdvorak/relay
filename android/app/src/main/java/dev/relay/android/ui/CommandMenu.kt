package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Check
import io.github.lyxnx.compose.ui.tablericons.outline.Search
import dev.relay.android.ui.theme.RelayTheme

data class CommandItem(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    /** Muted text on the right: a count, a path, a shortcut. */
    val hint: String? = null,
    val checked: Boolean = false,
    val enabled: Boolean = true,
)

data class CommandGroup(val heading: String?, val items: List<CommandItem>)

/**
 * A command menu: a 48dp search row over grouped, inset rows. Filters by label itself unless
 * `filter` is false (the caller filters, e.g. remotely).
 */
@Composable
fun CommandMenu(
    query: TextFieldState,
    groups: List<CommandGroup>,
    onSelect: (CommandItem) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    empty: String = "Nothing found",
    filter: Boolean = true,
    framed: Boolean = true,
) {
    val c = RelayTheme.colors
    val needle = query.text.trim()
    val shown = if (!filter || needle.isEmpty()) groups else groups.map { g ->
        g.copy(items = g.items.filter { it.label.contains(needle, ignoreCase = true) })
    }.filter { it.items.isNotEmpty() }
    Column(
        modifier
            .then(if (framed) Modifier.clip(RelayTheme.shapes.sheet).background(c.raised) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(TablerIcons.Outline.Search, null, tint = c.inkSoft, size = 20.dp)
            BasicTextField(
                query,
                Modifier.weight(1f),
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = RelayTheme.type.row.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorator = { field ->
                    Box {
                        if (query.text.isEmpty()) Text(placeholder, style = RelayTheme.type.row, color = c.inkFaint)
                        field()
                    }
                },
            )
        }
        LazyColumn(Modifier.fillMaxWidth().padding(6.dp)) {
            if (shown.isEmpty()) item {
                Text(empty, Modifier.fillMaxWidth().padding(vertical = 20.dp), style = RelayTheme.type.meta, color = c.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            shown.forEach { group ->
                if (group.heading != null) item(key = "h:${group.heading}") {
                    SectionLabel(group.heading, Modifier.padding(bottom = 4.dp))
                }
                items(group.items, key = { "${group.heading}:${it.id}" }) { CommandRow(it) { onSelect(it) } }
            }
        }
    }
}

@Composable
private fun CommandRow(item: CommandItem, onClick: () -> Unit) {
    val c = RelayTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RelayTheme.shapes.row)
            .pressable(onClick, enabled = item.enabled, scale = false, interaction = interaction)
            .background(if (pressed) c.selected else androidx.compose.ui.graphics.Color.Transparent)
            .heightIn(min = 48.dp)
            .padding(horizontal = RowTextInset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (item.icon != null) Icon(item.icon, null, tint = c.inkSoft, size = 20.dp)
        Text(item.label, Modifier.weight(1f), style = RelayTheme.type.row, color = if (item.enabled) c.ink else c.inkFaint, maxLines = 1)
        if (item.hint != null) Text(item.hint, style = RelayTheme.type.meta, color = c.inkSoft, maxLines = 1)
        if (item.checked) Icon(TablerIcons.Outline.Check, null, tint = c.ink, size = 18.dp)
    }
}
