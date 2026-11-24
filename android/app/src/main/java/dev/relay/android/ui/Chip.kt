package dev.relay.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.relay.android.ui.theme.RelayTheme

/**
 * A control that opens a choice (the composer's model and effort), not a badge: 36dp, tool-filled,
 * fully round. Two chips side by side join into one pill with [PairShapes.start] and [PairShapes.end].
 */
@Composable
fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    shape: Shape = RelayTheme.shapes.circle,
    enabled: Boolean = true,
) {
    val c = RelayTheme.colors
    CircleSurface(onClick, text, modifier.height(36.dp), shape, CircleTone.Tool, enabled, Haptic.Selection) {
        Row(
            Modifier.padding(start = if (leadingIcon != null) 10.dp else 14.dp, end = if (trailingIcon != null) 10.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) Icon(leadingIcon, null, tint = c.inkSoft, size = 16.dp)
            Text(text, style = RelayTheme.type.meta.copy(fontSize = 13.sp), color = c.ink, maxLines = 1)
            if (trailingIcon != null) Icon(trailingIcon, null, tint = c.inkSoft, size = 16.dp)
        }
    }
}
