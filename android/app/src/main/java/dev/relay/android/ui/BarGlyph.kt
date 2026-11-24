package dev.relay.android.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.tan

/** The bar's icons, drawn here because their two parts move and colour on their own. */
enum class BarGlyph { Threads, Projects, Pulls }

/** How long an icon's move takes once its tab is picked. */
internal const val GLYPH_MOVE_MS = 500

/** The icons are drawn on a 24-unit square. */
private const val GRID = 24f
private const val LINE = 1.7f

/** Past the end and back: what pops in. */
private val Pop = CubicBezierEasing(0.2f, 1.5f, 0.4f, 1f)
private val Settle = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)

private class GlyphPaths {
    val bubbleBack = path("M10.5 3.5h8a2.5 2.5 0 0 1 2.5 2.5v8.2l-2.6-2h-7.9A2.5 2.5 0 0 1 8 9.7V6a2.5 2.5 0 0 1 2.5-2.5z")
    val bubbleFront = path("M5.5 9h7A2.5 2.5 0 0 1 15 11.5v3.7a2.5 2.5 0 0 1-2.5 2.5H5.8L3 20.2v-8.7A2.5 2.5 0 0 1 5.5 9z")
    val folderBack = path("M9 3.5h2.7l1.8 2H19A2 2 0 0 1 21 7.5v7a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z")
    val folderFront = path("M3.8 9.5h12.4a1.5 1.5 0 0 1 1.5 1.7l-.9 7.5a2 2 0 0 1-2 1.8H5.2a2 2 0 0 1-2-1.8l-.9-7.5a1.5 1.5 0 0 1 1.5-1.7z")
    val trunk = path("M6 8.5v7")
    val branch = path("M18 15.5V9a3 3 0 0 0-3-3h-3.5")
    val arrow = path("M14 3.5 11.5 6 14 8.5")

    private fun path(data: String): Path = PathParser().parsePathString(data).toPath()
}

/** What a glyph paints with: [line] at rest over [ground], [accent] and [soft] once chosen. */
internal class GlyphColors(val line: Color, val ground: Color, val accent: Color, val soft: Color)

/** [chosen] fades the colours in, [move] (0 to 1) plays the icon's move; both are read while drawing. */
@Composable
internal fun BarGlyphIcon(glyph: BarGlyph, colors: GlyphColors, chosen: () -> Float, move: () -> Float, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val paths = remember { GlyphPaths() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / GRID, Offset.Zero) {
            val pen = Pen(colors, chosen().coerceIn(0f, 1f))
            when (glyph) {
                BarGlyph.Threads -> threads(paths, pen, move())
                BarGlyph.Projects -> projects(paths, pen, move())
                BarGlyph.Pulls -> pulls(paths, pen, move())
            }
        }
    }
}

private class Pen(private val c: GlyphColors, private val on: Float) {
    val stroke = Stroke(LINE, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val front get() = lerp(c.line, c.accent, on)
    val back get() = lerp(c.line, c.soft, on)
    /** The front part hides the back one's outline, chosen or not. */
    val frontFill get() = lerp(c.ground, c.accent, on)
    val backFill get() = c.soft.copy(alpha = c.soft.alpha * on)
}

private fun DrawScope.solid(path: Path, fill: Color, line: Color, pen: Pen) {
    drawPath(path, fill, style = Fill)
    drawPath(path, line, style = pen.stroke)
}

/** The part of the move between [from] and [to], 0 to 1. */
private fun part(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** The back bubble pops in while the front one springs up and sways off its tail. */
private fun DrawScope.threads(paths: GlyphPaths, pen: Pen, t: Float) {
    scale(lerp(0.3f, 1f, Pop.transform(part(t, 0.12f, 0.96f))), Offset(14.5f, 8.8f)) {
        solid(paths.bubbleBack, pen.backFill, pen.back, pen)
    }
    val sway = if (t < 0.6f) lerp(-14f, 6f, Settle.transform(part(t, 0f, 0.6f))) else lerp(6f, 0f, Settle.transform(part(t, 0.6f, 1f)))
    val tail = Offset(3f, 20.2f)
    rotate(sway, tail) {
        scale(lerp(0.5f, 1f, Pop.transform(part(t, 0f, 0.8f))), tail) {
            solid(paths.bubbleFront, pen.frontFill, pen.front, pen)
        }
    }
}

/** The front leans open while what's behind it rises out. */
private fun DrawScope.projects(paths: GlyphPaths, pen: Pen, t: Float) {
    val rise = if (t < 0.5f) lerp(5f, -3f, Settle.transform(part(t, 0f, 0.5f))) else lerp(-3f, 0f, Settle.transform(part(t, 0.5f, 1f)))
    translate(0f, rise) { solid(paths.folderBack, pen.backFill, pen.back, pen) }
    val open = if (t < 0.4f) Settle.transform(part(t, 0f, 0.4f)) else 1f - Settle.transform(part(t, 0.4f, 1f))
    val hinge = Offset(10f, 20.5f)
    val lean = Matrix().apply {
        values[Matrix.ScaleY] = lerp(1f, 0.72f, open)
        values[Matrix.SkewX] = tan(Math.toRadians(-7.0)).toFloat() * open
    }
    withTransform({
        translate(hinge.x, hinge.y)
        transform(lean)
        translate(-hinge.x, -hinge.y)
    }) { solid(paths.folderFront, pen.frontFill, pen.front, pen) }
}

/** The lines draw themselves from end to end, and the commits pop in as they get there. */
private fun DrawScope.pulls(paths: GlyphPaths, pen: Pen, t: Float) {
    val drawn = Settle.transform(part(t, 0.1f, 0.9f))
    for (line in listOf(paths.trunk, paths.branch, paths.arrow)) drawPath(line.upTo(drawn), pen.front, style = pen.stroke)
    for (at in listOf(Offset(6f, 6f), Offset(6f, 18f))) commit(at, pen.frontFill, pen.front, Pop.transform(part(t, 0f, 0.8f)), pen)
    commit(Offset(18f, 18f), pen.backFill, pen.back, Pop.transform(part(t, 0.3f, 1f)), pen)
}

private fun DrawScope.commit(at: Offset, fill: Color, line: Color, grown: Float, pen: Pen) {
    val radius = 2.5f * lerp(0.3f, 1f, grown)
    drawCircle(fill, radius, at)
    drawCircle(line, radius, at, style = pen.stroke)
}

private fun Path.upTo(share: Float): Path {
    if (share >= 1f) return this
    val measure = PathMeasure().also { it.setPath(this, forceClosed = false) }
    return Path().also { measure.getSegment(0f, measure.length * share, it, startWithMoveTo = true) }
}
