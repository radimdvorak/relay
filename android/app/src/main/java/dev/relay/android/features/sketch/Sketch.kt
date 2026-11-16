package dev.relay.android.features.sketch

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

/** What a stroke does to the photo under it. */
enum class Ink { Pen, Blur, Redact }

/**
 * One finger's line over a photo, in the photo's own pixels so it survives any
 * on-screen scaling. A closed blur or redact covers its whole inside.
 */
data class Stroke(
    val ink: Ink,
    val color: Int,
    val width: Float,
    val points: List<Offset>,
    val closed: Boolean = false,
)

/** The photo before any ink and the strokes over it, kept so a second edit can take strokes back. */
data class Sketched(val original: String, val strokes: List<Stroke>)

val penColors = listOf(
    0xFFFF3B30.toInt(),
    0xFFFF9500.toInt(),
    0xFFFFD100.toInt(),
    0xFF34C759.toInt(),
    0xFF007AFF.toInt(),
    0xFFFFFFFF.toInt(),
    0xFF1A1A1A.toInt(),
)

/** Brush widths in on-screen dp; the stroke keeps them in photo pixels. */
fun widthFor(ink: Ink): Float = when (ink) {
    Ink.Pen -> 4f
    Ink.Blur -> 30f
    Ink.Redact -> 16f
}

/**
 * A blur or redact that comes back to where it started covers what it went
 * around: the ends meet within [reach] and it went well beyond that.
 */
fun closesUp(ink: Ink, points: List<Offset>, reach: Float): Boolean {
    if (ink == Ink.Pen || points.size < 8) return false
    val ends = points.first() - points.last()
    val endsMeet = hypot(ends.x, ends.y) <= reach
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val wentAround = hypot(maxX - minX, maxY - minY) > reach * 3
    return endsMeet && wentAround
}

/** The strokes of one photo with what was undone, for redo; any new stroke drops the undone ones. */
data class StrokeHistory(val strokes: List<Stroke>, val undone: List<Stroke> = emptyList()) {
    val canUndo get() = strokes.isNotEmpty()
    val canRedo get() = undone.isNotEmpty()

    fun add(stroke: Stroke) = StrokeHistory(strokes + stroke)

    fun undo() = if (canUndo) StrokeHistory(strokes.dropLast(1), undone + strokes.last()) else this

    fun redo() = if (canRedo) StrokeHistory(strokes + undone.last(), undone.dropLast(1)) else this
}
