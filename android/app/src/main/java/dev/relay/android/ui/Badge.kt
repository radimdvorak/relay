package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayTheme

/** Status colours: colour in the UI means a status. */
enum class Tone { Neutral, Success, Info, Warning, Destructive, Waiting }

fun Tone.color(c: RelayColors): Color = when (this) {
    Tone.Neutral -> c.inkSoft
    Tone.Success -> c.success
    Tone.Info -> c.info
    Tone.Warning -> c.warning
    Tone.Destructive -> c.danger
    Tone.Waiting -> c.waiting
}

enum class BadgeSize(val height: Dp, val radius: Dp) { Sm(18.dp, 5.dp), Default(20.dp, 6.dp), Lg(24.dp, 7.dp) }

/**
 * A status as text: a dot (or icon) in the tone's colour and a soft label beside it, no fill and
 * no outline, since the brief has no pill badges. Use sparingly: a status, not decoration.
 */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    icon: ImageVector? = null,
    dot: Boolean = tone != Tone.Neutral && icon == null,
    size: BadgeSize = BadgeSize.Default,
) {
    val c = RelayTheme.colors
    Row(
        modifier.height(size.height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = tone.color(c), size = 12.dp)
        else if (dot) Box(Modifier.size(6.dp).background(tone.color(c), CircleShape))
        Text(
            text,
            style = if (size == BadgeSize.Lg) RelayTheme.type.meta else RelayTheme.type.label,
            color = c.inkSoft,
            maxLines = 1,
        )
    }
}

/** A bare status dot, for rows where a badge would be too loud. */
@Composable
fun StatusDot(tone: Tone, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).background(tone.color(RelayTheme.colors), CircleShape))
}
