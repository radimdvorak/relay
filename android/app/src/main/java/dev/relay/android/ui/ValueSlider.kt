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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SliderHeight = 56.dp
private val Rim = 3.dp
private val FillPastLine = 16.dp
private val ThumbRadius = 14.dp
private val ThumbBehindLine = 17.dp
private val LineHeight = 24.dp
/** Dots along the track, only to read the distance by. */
private const val DOTS = 5

private val settle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * [EffortSlider]'s sibling for an amount with no stops: the same capsule filled in strong, disc and
 * line, moving freely over [range] in whole steps. [marked], the value it comes with, is a ring on
 * the track with a tick of haptics on the way past. Tells [onValueChange] as it moves.
 */
@Composable
fun ValueSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..100,
    marked: Int? = null,
) {
    val c = RelayTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(value.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)

    LaunchedEffect(value) {
        val elsewhere = abs(position.value - value) >= 1f
        if (!dragging && elsewhere) position.animateTo(value.toFloat(), settle)
    }

    fun move(to: Int) {
        val from = current
        if (to == from) return
        val passesMark = marked != null && (to == marked || (marked - from) * (marked - to) < 0)
        if (passesMark) haptics.perform(Haptic.Selection)
        change(to)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), range.first.toFloat()..range.last.toFloat())
                setProgress { target ->
                    move(target.roundToInt().coerceIn(range))
                    true
                }
            }
            .pointerInput(range) {
                detectTapGestures { tap -> move(ValueTrack(this, size.width.toFloat(), range).value(tap.x).roundToInt()) }
            }
            .pointerInput(range) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { pointer, dx ->
                    pointer.consume()
                    val track = ValueTrack(this, size.width.toFloat(), range)
                    val next = (position.value + dx / track.perStep).coerceIn(range.first.toFloat(), range.last.toFloat())
                    scope.launch { position.snapTo(next) }
                    move(next.roundToInt())
                }
            },
    ) {
        drawAmount(c, ValueTrack(this, size.width, range), position.value, marked)
    }
}

/** Where values sit along a slider [width] px wide: the line from one round end to the other. */
private class ValueTrack(density: Density, width: Float, val range: IntRange) {
    val rim = with(density) { Rim.toPx() }
    val left = rim
    val right = width - rim
    // At the lowest value the fill is a circle, so it never gets thinner than the track's end.
    private val first = with(density) { left + (SliderHeight - Rim * 2).toPx() - FillPastLine.toPx() }
    private val last = right - with(density) { FillPastLine.toPx() }
    val perStep = (last - first) / (range.last - range.first)

    fun x(value: Float) = first + (value - range.first) * perStep

    fun value(x: Float) = (range.first + (x - first) / perStep).coerceIn(range.first.toFloat(), range.last.toFloat())
}

private fun DrawScope.drawAmount(c: RelayColors, track: ValueTrack, value: Float, marked: Int?) {
    val inner = size.height - 2 * track.rim
    val centreY = size.height / 2
    drawRoundRect(c.selected, cornerRadius = CornerRadius(size.height / 2))

    val lineX = track.x(value)
    val fillEnd = lineX + FillPastLine.toPx()
    drawRoundRect(
        c.strong,
        topLeft = Offset(track.left, track.rim),
        size = Size(fillEnd - track.left, inner),
        cornerRadius = CornerRadius(inner / 2),
    )

    val radius = ThumbRadius.toPx()
    val discX = (lineX - ThumbBehindLine.toPx()).coerceAtLeast(track.left + radius + 4.dp.toPx())
    val underThumb = (discX - radius)..(lineX + 4.dp.toPx())
    for (i in 0 until DOTS) {
        val at = track.range.first + (track.range.last - track.range.first) * i / (DOTS - 1f)
        val x = track.x(at)
        if (x in underThumb) continue
        val covered = x < fillEnd
        if (marked != null && at.roundToInt() == marked) drawCircle(
            if (covered) lerp(c.strong, c.onStrong, 0.75f) else c.inkSoft,
            radius = 4.dp.toPx(),
            center = Offset(x, centreY),
            style = Stroke(1.5.dp.toPx()),
        )
        else drawCircle(
            if (covered) lerp(c.strong, c.onStrong, 0.5f) else c.inkFaint,
            radius = 2.dp.toPx(),
            center = Offset(x, centreY),
        )
    }

    drawCircle(lerp(c.strong, c.onStrong, 0.15f), radius, Offset(discX, centreY))
    val half = LineHeight.toPx() / 2
    drawLine(c.onStrong, Offset(lineX, centreY - half), Offset(lineX, centreY + half), 2.dp.toPx(), StrokeCap.Round)
}
