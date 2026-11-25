package dev.relay.android.features.composer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme

private val Pitch = 8.dp

/** The effort as a tiny slider: a dot per stop, the chosen one a short upright bar. */
@Composable
internal fun EffortGlyph(stops: Int, chosen: Int, modifier: Modifier = Modifier, color: Color = RelayTheme.colors.ink) {
    val dots = RelayTheme.colors.inkSoft
    Canvas(modifier.width(Pitch * (stops - 1).coerceAtLeast(0) + 4.dp).height(12.dp)) {
        val middle = size.height / 2
        for (i in 0 until stops) {
            val x = 2.dp.toPx() + i * Pitch.toPx()
            if (i == chosen) {
                val bar = Size(2.dp.toPx(), 11.dp.toPx())
                drawRoundRect(color, Offset(x - bar.width / 2, middle - bar.height / 2), bar, CornerRadius(bar.width / 2))
            } else drawCircle(dots, 1.5.dp.toPx(), Offset(x, middle))
        }
    }
}
