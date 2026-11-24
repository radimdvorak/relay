package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ChevronRight
import dev.relay.android.ui.theme.RelayTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow

/*
 * Lists sit [ListInset] in from the screen's edges; each row is a rounded patch that lights up
 * when pressed or chosen, its text [RowTextInset] further in, so it lines up with [SectionLabel].
 * No cards, no rules between rows: whitespace and the occasional label separate them.
 */

/** How far a list's rows sit in from the screen's edges; a screen pads its list with it. */
val ListInset = 8.dp

/** A screen's list: [ListInset] at the sides, [bottom] below the last row. */
fun listPadding(bottom: Dp = 40.dp, top: Dp = 4.dp) = PaddingValues(start = ListInset, end = ListInset, top = top, bottom = bottom)

/** Where a row's content starts inside it: 24dp from the screen's edge with [ListInset]. */
val RowTextInset = 16.dp

/** A group's label with how many it holds. */
@Deprecated("use SectionLabel", ReplaceWith("SectionLabel(text, modifier, countText, action = action)"))
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    /** The count as shown, when it isn't just the number ("30+"). */
    countText: String? = count?.toString(),
    action: (@Composable () -> Unit)? = null,
) {
    SectionLabel(text, modifier, countText, action = action)
}

/** Rows of one group, stacked with 2dp between them. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

/** A 40dp item tile: an icon on the tool fill. */
@Composable
fun MediaTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = RelayTheme.colors.ink,
) {
    Box(
        modifier.size(size).clip(RelayTheme.shapes.row).background(RelayTheme.colors.tool),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, size = if (size >= 40.dp) 20.dp else 16.dp) }
}

/** Default rows hold a title and a subtitle; Sm one line; Xs is for menus and dense lists. */
enum class RowSize(val minHeight: Dp, val padY: Dp) { Default(60.dp, 10.dp), Sm(48.dp, 8.dp), Xs(40.dp, 6.dp) }

/**
 * A list row: a leading icon (20dp, soft ink) or slot, a 16sp title, a subtitle in the soft ink,
 * then [meta] (a time, a count) and whatever trails. Pressed it lights up; [selected] keeps it lit.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    meta: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    selected: Boolean = false,
    size: RowSize = RowSize.Default,
    titleColor: Color = RelayTheme.colors.ink,
    subtitleLines: Int = 2,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val c = RelayTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lit = pressed || selected
    val tappable = onClick != null || onLongClick != null
    Row(
        modifier
            .fillMaxWidth()
            .clip(RelayTheme.shapes.row)
            .semantics { this.selected = selected }
            .background(if (lit) c.selected else Color.Transparent)
            .then(if (tappable) Modifier.pressable(onClick ?: {}, onLongClick = onLongClick, scale = false, interaction = interaction) else Modifier)
            .heightIn(min = size.minHeight)
            .padding(horizontal = RowTextInset, vertical = size.padY),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            leading != null -> Box(Modifier.widthIn(min = 20.dp), contentAlignment = Alignment.Center) { leading() }
            leadingIcon != null -> Icon(leadingIcon, null, tint = c.inkSoft, size = 20.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = RelayTheme.type.row, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(
                subtitle,
                style = RelayTheme.type.meta,
                color = c.inkSoft,
                maxLines = subtitleLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (meta != null) Text(meta, style = RelayTheme.type.meta, color = c.inkSoft, maxLines = 1)
        if (trailing != null) trailing()
        if (chevron) Icon(TablerIcons.Outline.ChevronRight, null, tint = c.inkFaint, size = 16.dp)
    }
}
