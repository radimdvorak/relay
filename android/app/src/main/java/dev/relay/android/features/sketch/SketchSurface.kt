package dev.relay.android.features.sketch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.min

/** Where the photo sits in the view: its scale and top-left corner, fitted and centred. */
private data class Fit(val scale: Float, val origin: Offset) {
    fun toPhoto(at: Offset) = (at - origin) / scale
}

/** A stroke has to move this far, in screen pixels, before a new point is kept. */
private const val STEP_PX = 2f

/** Its ends meeting within this many dp closes a blur or redact. */
private const val CLOSE_DP = 28f

/**
 * The photo fitted into the space, drawn on with one finger. A second finger
 * calls the stroke off, so a pinch or a palm doesn't leave a mark.
 */
@Composable
internal fun SketchSurface(
    painter: SketchPainter,
    strokes: List<Stroke>,
    ink: Ink,
    color: Int,
    onStroke: (Stroke) -> Unit,
) {
    val density = LocalDensity.current.density
    var live by remember { mutableStateOf<Stroke?>(null) }
    val brush by rememberUpdatedState(ink to color)
    val finish by rememberUpdatedState(onStroke)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val photo = painter.photo
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val scale = min(width / photo.width, height / photo.height)
        val fit = Fit(scale, Offset((width - photo.width * scale) / 2, (height - photo.height * scale) / 2))
        val currentFit by rememberUpdatedState(fit)

        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val (strokeInk, strokeColor) = brush
                    val f = currentFit
                    var stroke = Stroke(strokeInk, strokeColor, widthFor(strokeInk) * density / f.scale, listOf(f.toPhoto(down.position)))
                    live = stroke
                    down.consume()
                    var lastAt = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) {
                            live = null
                            event.changes.forEach { it.consume() }
                            return@awaitEachGesture
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        if ((change.position - lastAt).getDistance() < STEP_PX) continue
                        lastAt = change.position
                        stroke = stroke.copy(points = stroke.points + f.toPhoto(change.position))
                        live = stroke
                    }
                    live = null
                    finish(stroke.copy(closed = closesUp(stroke.ink, stroke.points, CLOSE_DP * density / f.scale)))
                }
            },
        ) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.translate(fit.origin.x, fit.origin.y)
                native.scale(fit.scale, fit.scale)
                native.clipRect(0f, 0f, photo.width.toFloat(), photo.height.toFloat())
                painter.draw(native, strokes)
                live?.let { painter.draw(native, it) }
                native.restore()
            }
        }
    }
}
