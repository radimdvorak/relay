package dev.relay.android.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

private val StartFade = 24.dp
private val EndFade = 64.dp
private val SideFade = 32.dp

/** What [scroll] cuts off at either end goes into [over] rather than stopping at a line. Put it before the scroll. */
internal fun Modifier.fadesCutEnds(scroll: ScrollState, over: Color) = drawWithContent {
    drawContent()
    fadeStart(over, hidden = scroll.value.toFloat())
    fadeEnd(over, at = size.height, hidden = (scroll.maxValue - scroll.value).toFloat())
}

/** What [scroll] cuts off at its side goes into [over]. Put it before the scroll. */
internal fun Modifier.fadesCutSide(scroll: ScrollState, over: Color) = drawWithContent {
    drawContent()
    val fade = SideFade.toPx()
    val before = (scroll.value / fade).coerceIn(0f, 1f)
    val after = ((scroll.maxValue - scroll.value) / fade).coerceIn(0f, 1f)
    val clear = over.copy(alpha = 0f)
    if (before > 0f) drawRect(
        Brush.horizontalGradient(listOf(over.copy(alpha = before), clear), startX = 0f, endX = fade),
        size = Size(fade, size.height),
    )
    if (after > 0f) drawRect(
        Brush.horizontalGradient(listOf(clear, over.copy(alpha = after)), startX = size.width - fade, endX = size.width),
        topLeft = Offset(size.width - fade, 0f),
        size = Size(fade, size.height),
    )
}

/** Into [over] toward the start; comes in over the first of what's [hidden] before it. */
private fun DrawScope.fadeStart(over: Color, hidden: Float) {
    val height = StartFade.toPx()
    val strength = (hidden / height).coerceIn(0f, 1f)
    drawRect(
        Brush.verticalGradient(listOf(over.copy(alpha = strength), over.copy(alpha = 0f)), startY = 0f, endY = height),
        size = Size(size.width, height),
    )
}

/** Into [over] toward [at]; comes in over the first of what's [hidden] past it. */
internal fun DrawScope.fadeEnd(over: Color, at: Float, hidden: Float) {
    val height = EndFade.toPx()
    val strength = (hidden / height).coerceIn(0f, 1f)
    drawRect(
        Brush.verticalGradient(listOf(over.copy(alpha = 0f), over.copy(alpha = strength)), startY = at - height, endY = at),
        topLeft = Offset(0f, at - height),
        size = Size(size.width, height),
    )
}
