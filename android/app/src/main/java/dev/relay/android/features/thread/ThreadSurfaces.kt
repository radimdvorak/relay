package dev.relay.android.features.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.Text
import dev.relay.android.ui.pressable
import dev.relay.android.ui.floatShadow
import dev.relay.android.ui.theme.RelayTheme

/** How far the conversation's text sits from the screen's edges. */
internal val ThreadInset = 18.dp

/** How far the cards over the composer sit from the screen's edges, in line with it. */
internal val DockInset = 16.dp

/** A quiet container in the conversation: what a turn leaves beside its text. */
@Composable
internal fun QuietCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    gap: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RelayTheme.shapes.card).background(RelayTheme.colors.container).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** A strip over the composer. */
@Composable
internal fun Modifier.dockStrip(): Modifier =
    clip(RelayTheme.shapes.toast).background(RelayTheme.colors.container)

/** A card over the composer that waits on you: it floats like the composer, washed with [tint]. */
@Composable
internal fun Modifier.dockCard(tint: Color): Modifier {
    val c = RelayTheme.colors
    val shape = RelayTheme.shapes.composer
    val fill = tint.copy(alpha = if (c.isDark) 0.14f else 0.10f).compositeOver(c.raised)
    return this
        .floatShadow(shape)
        .clip(shape)
        .background(fill)
}

/** How much of the thread's end fades into the dock over it. */
private val EndFade = 16.dp

/**
 * Fades the thread's last lines out where the dock begins. Drawn over the list's end, so it takes
 * no room and the list is measured as before.
 */
@Composable
internal fun Modifier.fadesThreadEnd(): Modifier {
    val surface = RelayTheme.colors.surface
    return drawBehind {
        val fade = EndFade.toPx()
        drawRect(
            Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface), startY = -fade, endY = 0f),
            topLeft = Offset(0f, -fade),
            size = Size(size.width, fade),
        )
    }
}

/** A word that does something, in a row of text: "Show all", "Reopen". */
@Composable
internal fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = RelayTheme.colors.inkSoft,
    enabled: Boolean = true,
) {
    Text(
        label,
        modifier.pressable(onClick = onClick, enabled = enabled, scale = false).padding(vertical = 4.dp),
        style = RelayTheme.type.meta.copy(fontWeight = FontWeight.Medium),
        color = color,
        maxLines = 1,
    )
}
