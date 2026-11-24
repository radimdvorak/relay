package dev.relay.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayColors
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SliderHeight = 63.dp
private val Rim = 3.dp
/** The first stop sits this far into the track, the last this far from its end (measured off the Claude app). */
private val FirstStop = 46.dp
private val LastStopFromEnd = 16.dp
private val ThumbRadius = 15.5.dp
/** The disc trails the white line, which marks the level itself. */
private val ThumbBehindLine = 18.dp
private val FillPastLine = 16.dp
private val LineHeight = 28.dp

/** Settles on a stop in about 250ms and never overshoots it. */
private val settle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * The effort sheet's chunky slider: a capsule filled in strong up to the chosen stop, a disc
 * with a white line riding the fill's end, and [levels] stops as dots ([marked], the model's
 * default, as a ring). Drags are continuous; [onPreview] hears each stop the drag crosses (for
 * the label above), and on release it settles on the nearest stop and reports it to [onLevelChange].
 */
@Composable
fun EffortSlider(
    level: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    levels: Int = 5,
    marked: Int? = null,
    onPreview: (Int) -> Unit = {},
) {
    val c = RelayTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(level.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var previewed by remember { mutableIntStateOf(level) }
    val change by rememberUpdatedState(onLevelChange)
    val preview by rememberUpdatedState(onPreview)
    val last = levels - 1

    LaunchedEffect(level) {
        previewed = level
        if (!dragging) position.animateTo(level.toFloat(), settle)
    }

    fun previewNearest() {
        val nearest = position.value.roundToInt()
        if (nearest == previewed) return
        previewed = nearest
        haptics.perform(Haptic.Selection)
        preview(nearest)
    }

    fun commit(target: Int) {
        dragging = false
        scope.launch { position.animateTo(target.toFloat(), settle) }
        if (previewed != target) {
            previewed = target
            preview(target)
        }
        change(target)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(level.toFloat(), 0f..last.toFloat(), steps = levels - 2)
                setProgress { value ->
                    commit(value.roundToInt().coerceIn(0, last))
                    true
                }
            }
            .pointerInput(levels) {
                detectTapGestures { tap -> commit(StopTrack(this, size.width.toFloat(), levels).nearest(tap.x)) }
            }
            .pointerInput(levels) {
                val track = StopTrack(this, size.width.toFloat(), levels)
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { commit(position.value.roundToInt()) },
                    onDragCancel = { commit(position.value.roundToInt()) },
                ) { pointer, dx ->
                    pointer.consume()
                    val next = (position.value + dx / track.step).coerceIn(0f, last.toFloat())
                    scope.launch { position.snapTo(next) }
                    previewNearest()
                }
            },
    ) {
        drawEffort(c, StopTrack(this, size.width, levels), position.value, marked)
    }
}

/** Where the stops sit along a slider [width] px wide. */
private class StopTrack(density: Density, width: Float, val levels: Int) {
    val rim = with(density) { Rim.toPx() }
    val left = rim
    val right = width - rim
    private val first = left + with(density) { FirstStop.toPx() }
    private val lastX = right - with(density) { LastStopFromEnd.toPx() }
    val step = (lastX - first) / (levels - 1).coerceAtLeast(1)

    fun x(position: Float) = first + position * step

    fun nearest(x: Float) = ((x - first) / step).roundToInt().coerceIn(0, levels - 1)
}

private fun DrawScope.drawEffort(c: RelayColors, track: StopTrack, position: Float, marked: Int?) {
    val h = size.height
    val inner = h - 2 * track.rim
    val centreY = h / 2
    drawRoundRect(c.tool, cornerRadius = CornerRadius(h / 2))
    drawRoundRect(
        lerp(c.surface, c.tool, 0.55f),
        topLeft = Offset(track.left, track.rim),
        size = Size(track.right - track.left, inner),
        cornerRadius = CornerRadius(inner / 2),
    )

    val lineX = track.x(position)
    val fillEnd = (lineX + FillPastLine.toPx()).coerceIn(track.left + inner, track.right)
    drawRoundRect(
        c.strong,
        topLeft = Offset(track.left, track.rim),
        size = Size(fillEnd - track.left, inner),
        cornerRadius = CornerRadius(inner / 2),
    )

    val radius = ThumbRadius.toPx()
    val discX = (lineX - ThumbBehindLine.toPx()).coerceAtLeast(track.left + radius + 4.dp.toPx())
    val underThumb = (discX - radius)..(lineX + 4.dp.toPx())
    val onFill = lerp(c.strong, c.onStrong, 0.5f)
    for (i in 0 until track.levels) {
        val x = track.x(i.toFloat())
        if (x in underThumb) continue
        val covered = x < fillEnd
        if (i == marked) drawCircle(
            if (covered) lerp(c.strong, c.onStrong, 0.75f) else c.inkSoft,
            radius = 4.dp.toPx(),
            center = Offset(x, centreY),
            style = Stroke(1.5.dp.toPx()),
        )
        else drawCircle(if (covered) onFill else c.inkFaint, radius = 2.dp.toPx(), center = Offset(x, centreY))
    }

    drawCircle(lerp(c.strong, c.onStrong, 0.15f), radius, Offset(discX, centreY))
    val half = LineHeight.toPx() / 2
    drawLine(c.onStrong, Offset(lineX, centreY - half), Offset(lineX, centreY + half), 2.dp.toPx(), StrokeCap.Round)
}
