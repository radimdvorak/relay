package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme

enum class CardVariant { Default, Outline, Muted }

/**
 * A tonal card: the quiet container fill, 20dp corners, no outline or shadow. Outline is a bare
 * frame in the line colour; Muted the tool fill. Padding and gap are 24 (pass 16 for the small
 * one). Tappable when `onClick` is set.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.Default,
    padding: Dp = 24.dp,
    gap: Dp = padding,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = RelayTheme.colors
    val fill = when (variant) {
        CardVariant.Default -> c.container
        CardVariant.Outline -> Color.Transparent
        CardVariant.Muted -> c.tool
    }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressable(onClick, onLongClick = onLongClick) else Modifier)
            .clip(RelayTheme.shapes.card)
            .background(fill)
            .then(if (variant == CardVariant.Outline) Modifier.border(1.dp, c.line, RelayTheme.shapes.card) else Modifier)
            .padding(PaddingValues(padding)),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** Title (16sp, 500) with a soft description under it, and an action on the right. */
@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = RelayTheme.type.row.copy(fontWeight = FontWeight.Medium))
            if (description != null) Text(description, style = RelayTheme.type.meta, color = RelayTheme.colors.inkSoft)
        }
        if (action != null) action()
    }
}
