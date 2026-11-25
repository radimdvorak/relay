package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.AlertTriangle
import io.github.lyxnx.compose.ui.tablericons.outline.CircleCheck
import io.github.lyxnx.compose.ui.tablericons.outline.CircleX
import io.github.lyxnx.compose.ui.tablericons.outline.InfoCircle
import io.github.lyxnx.compose.ui.tablericons.outline.X
import dev.relay.android.ui.theme.RelayTheme

/**
 * An inline strip on the quiet container fill, 20dp corners: an icon in the tone's colour, a
 * title and a soft line. Optional action and close.
 */
@Composable
fun Banner(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    tone: Tone = Tone.Info,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val c = RelayTheme.colors
    val glyph = icon ?: when (tone) {
        Tone.Success -> TablerIcons.Outline.CircleCheck
        Tone.Destructive -> TablerIcons.Outline.CircleX
        Tone.Warning, Tone.Waiting -> TablerIcons.Outline.AlertTriangle
        Tone.Info, Tone.Neutral -> TablerIcons.Outline.InfoCircle
    }
    Row(
        modifier.fillMaxWidth().background(c.container, RelayTheme.shapes.card)
            .padding(start = 16.dp, end = if (onDismiss != null) 8.dp else 16.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = if (message == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        Icon(glyph, null, Modifier.padding(top = if (message == null) 0.dp else 1.dp), tint = if (tone == Tone.Neutral) c.ink else tone.color(c), size = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = RelayTheme.type.meta.copy(fontWeight = FontWeight.Medium))
            if (message != null) Text(message, style = RelayTheme.type.meta, color = c.inkSoft)
        }
        if (actionLabel != null && onAction != null)
            Button(actionLabel, onAction, variant = ButtonVariant.Secondary, size = ButtonSize.Sm)
        if (onDismiss != null)
            CircleButton(TablerIcons.Outline.X, "Dismiss", onDismiss, size = CircleSize.Small, tone = CircleTone.Clear)
    }
}
