package dev.relay.android.features.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.relay.android.remote.files.shownFiles
import dev.relay.android.ui.theme.RelayShapes
import dev.relay.android.ui.theme.RelayTheme

private val SideRoom = 16.dp
private val TopRoom = 10.dp

/** Fully round around one line, [RelayShapes.bubble] corners around several; its size alone says which. */
private data class BubbleShape(private val oneLine: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val single = size.height <= with(density) { oneLine.toPx() } * 1.2f
        val corner = if (single) size.height / 2 else with(density) { RelayShapes.bubble.toPx() }
        return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(corner)))
    }
}

/** What you sent, on the right. [side]: a /btw question, a dashed outline and nothing else, as on the desktop. */
@Composable
internal fun UserBubble(text: String, side: Boolean, pending: Boolean) {
    val c = RelayTheme.colors
    val style = RelayTheme.type.body
    val code = RelayTheme.type.code
    val oneLine = with(LocalDensity.current) { style.lineHeight.toDp() } + TopRoom * 2
    val shape = remember(oneLine) { BubbleShape(oneLine) }
    val frame = if (side) Modifier.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val dashes = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
        onDrawBehind { drawOutline(outline, c.inkFaint, style = dashes) }
    } else Modifier.background(c.bubble, shape)
    // A file the message attached reads as its name, set like code, not its path on the computer.
    val shown = remember(text, c) {
        val files = shownFiles(text)
        val mark = SpanStyle(fontFamily = code.fontFamily, fontSize = code.fontSize, background = c.onBubble.copy(alpha = 0.07f))
        buildAnnotatedString {
            append(files.text)
            for (name in files.names) addStyle(mark, name.first, name.last + 1)
        }
    }
    SelectionContainer(
        Modifier.fillMaxWidth().wrapContentWidth(Alignment.End).fillMaxWidth(0.85f).wrapContentWidth(Alignment.End)
            .then(frame).alpha(if (pending) 0.6f else 1f).padding(horizontal = SideRoom, vertical = TopRoom),
    ) {
        androidx.compose.material3.Text(shown, style = style, color = if (side) c.ink else c.onBubble)
    }
}
