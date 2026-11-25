package dev.relay.android.features.settings

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.CircleTone
import dev.relay.android.ui.fill
import dev.relay.android.ui.theme.RelayColors

/**
 * Relay in miniature, painted in [palettes]: a thread with its header and composer. Two palettes
 * share it along a diagonal, for a theme that changes with the phone or the computer.
 */
@Composable
internal fun ThemePreview(palettes: List<RelayColors>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawThread(palettes.first())
        if (palettes.size > 1) clipPath(lowerRight()) { drawThread(palettes.last()) }
    }
}

private fun DrawScope.lowerRight() = Path().apply {
    moveTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

private fun DrawScope.drawThread(c: RelayColors) {
    // Laid out on a 76dp wide phone and scaled to whatever the tile gets.
    val unit = size.width / 76.dp.toPx()
    fun px(dp: Dp) = dp.toPx() * unit
    val w = size.width
    val side = px(8.dp)

    fun bar(color: Color, x: Float, y: Float, width: Float, height: Float, corner: Float = height / 2) =
        drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(corner))

    drawRect(c.chrome)
    val headerY = px(13.dp)
    val button = CircleTone.Raised.fill(c)
    drawCircle(button, px(5.dp), Offset(side + px(5.dp), headerY))
    drawCircle(button, px(5.dp), Offset(w - side - px(5.dp), headerY))
    bar(c.ink, w / 2 - px(9.dp), headerY - px(1.5.dp), px(18.dp), px(3.dp))

    val sheetTop = px(24.dp)
    drawRoundRect(c.surface, Offset(0f, sheetTop), Size(w, size.height), CornerRadius(px(9.dp)))

    val bubble = px(30.dp)
    bar(c.bubble, w - side - bubble, sheetTop + px(9.dp), bubble, px(11.dp))
    bar(c.onBubble, w - side - bubble + px(6.dp), sheetTop + px(13.dp), bubble - px(12.dp), px(3.dp))

    val answer = sheetTop + px(29.dp)
    bar(c.accent, side, answer, px(12.dp), px(3.dp))
    bar(c.ink, side + px(15.dp), answer, px(34.dp), px(3.dp))
    bar(c.ink, side, answer + px(8.dp), px(52.dp), px(3.dp))
    bar(c.inkSoft, side, answer + px(16.dp), px(38.dp), px(3.dp))

    val composer = px(16.dp)
    val composerTop = size.height - px(7.dp) - composer
    bar(c.tool, px(6.dp), composerTop, w - px(12.dp), composer)
    bar(c.inkFaint, px(13.dp), composerTop + composer / 2 - px(1.5.dp), px(18.dp), px(3.dp))
    drawCircle(c.send, px(5.5.dp), Offset(w - px(14.dp), composerTop + composer / 2))
}
