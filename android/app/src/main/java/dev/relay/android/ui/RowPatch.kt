package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme

/** A [ListRow]'s patch, press and insets, for a row that draws its own lines. */
@Composable
fun RowPatch(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    minHeight: Dp = RowSize.Default.minHeight,
    /** The end inset; smaller when a button with its own touch room trails. */
    end: Dp = RowTextInset,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .clip(RelayTheme.shapes.row)
            .background(if (pressed || selected) RelayTheme.colors.selected else Color.Transparent)
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
            .then(if (onClick != null) Modifier.pressable(onClick, scale = false, interaction = interaction) else Modifier)
            .heightIn(min = minHeight)
            .padding(start = RowTextInset, end = end, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
