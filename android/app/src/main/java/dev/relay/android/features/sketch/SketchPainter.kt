package dev.relay.android.features.sketch

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.scale
import kotlin.math.max
import kotlin.math.roundToInt

/** Long side of the copy a blur samples from: small enough that text in it can't be read back. */
private const val BLUR_SIDE = 96

/**
 * Draws a photo with its strokes, the same way on screen and into the image
 * that's sent, so what you see is what goes. Canvas units are photo pixels.
 */
class SketchPainter(val photo: Bitmap) {
    private val blurPaint: Paint by lazy {
        val factor = BLUR_SIDE.toFloat() / max(photo.width, photo.height)
        val small = photo.scale((photo.width * factor).roundToInt().coerceAtLeast(1), (photo.height * factor).roundToInt().coerceAtLeast(1))
        val shader = BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(photo.width.toFloat() / small.width, photo.height.toFloat() / small.height) })
        }
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: Canvas, strokes: List<Stroke>) {
        canvas.drawBitmap(photo, 0f, 0f, null)
        strokes.forEach { draw(canvas, it) }
    }

    fun draw(canvas: Canvas, stroke: Stroke) {
        val paint = if (stroke.ink == Ink.Blur) blurPaint else ink.apply { color = colorOf(stroke) }
        paint.style = if (stroke.closed) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        paint.strokeWidth = stroke.width
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(pathOf(stroke), paint)
    }

    /** The photo with its strokes burned in, at the photo's size. */
    fun burn(strokes: List<Stroke>): Bitmap {
        val out = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
        draw(Canvas(out), strokes)
        return out
    }

    private fun colorOf(stroke: Stroke) = if (stroke.ink == Ink.Redact) android.graphics.Color.BLACK else stroke.color
}

/** The points smoothed through their midpoints; a tap still leaves a dot. */
private fun pathOf(stroke: Stroke): Path {
    val points = stroke.points
    val path = Path()
    val first = points.firstOrNull() ?: return path
    path.moveTo(first.x, first.y)
    if (points.size == 1) {
        path.lineTo(first.x + 0.01f, first.y)
        return path
    }
    for (i in 1 until points.size - 1) {
        val here = points[i]
        val next = points[i + 1]
        path.quadTo(here.x, here.y, (here.x + next.x) / 2, (here.y + next.y) / 2)
    }
    val last = points.last()
    path.lineTo(last.x, last.y)
    if (stroke.closed) path.close()
    return path
}
