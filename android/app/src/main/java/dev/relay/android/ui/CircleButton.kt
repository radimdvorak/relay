package dev.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayShapes
import dev.relay.android.ui.theme.RelayTheme

/** Header circles are 44dp, composer circles 36dp; Small fits a row or a field. */
enum class CircleSize(val diameter: Dp, val icon: Dp) {
    Header(44.dp, 22.dp),
    Composer(36.dp, 20.dp),
    Small(28.dp, 16.dp),
}

/**
 * What a circle is filled with. Raised sits on the chrome (header buttons): lighter than it, so
 * the raised fill in light and the tool fill in dark, where raised is too close to the chrome.
 * Tool sits on the surface or inside the composer; Strong is the one dark action (send,
 * compose); Clear has no fill.
 */
enum class CircleTone { Raised, Tool, Strong, Clear }

internal fun CircleTone.fill(c: RelayColors): Color = when (this) {
    CircleTone.Raised -> if (c.isDark) c.tool else c.raised
    CircleTone.Tool -> c.tool
    CircleTone.Strong -> c.strong
    CircleTone.Clear -> Color.Transparent
}

internal fun CircleTone.content(c: RelayColors): Color = when (this) {
    CircleTone.Strong -> c.onStrong
    else -> c.ink
}

/** A pressed fill: a step toward the ink, so it shows on any tone (Clear gets the tool fill). */
internal fun pressedFill(fill: Color, c: RelayColors): Color =
    if (fill.alpha == 0f) c.tool else c.ink.copy(alpha = 0.07f).compositeOver(fill)

/** One round action: an icon in a circle, as the header and the composer use them. */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: CircleSize = CircleSize.Header,
    tone: CircleTone = CircleTone.Tool,
    enabled: Boolean = true,
    haptic: Haptic? = null,
) {
    CircleSurface(onClick, contentDescription, modifier.size(size.diameter), RelayShapes.circle, tone, enabled, haptic) {
        Icon(icon, null, size = size.icon)
    }
}

/** The fill, press and content colour every circle and pair half shares. */
@Composable
internal fun CircleSurface(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier,
    shape: Shape,
    tone: CircleTone,
    enabled: Boolean,
    haptic: Haptic?,
    content: @Composable () -> Unit,
) {
    val c = RelayTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = tone.fill(c)
    Box(
        modifier
            .pressable(onClick, enabled = enabled, haptic = haptic, interaction = interaction)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (pressed && enabled) pressedFill(fill, c) else fill)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ContentColor(tone.content(c), content)
    }
}
