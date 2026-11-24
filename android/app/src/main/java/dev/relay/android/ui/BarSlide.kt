package dev.relay.android.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far past the first or last tab the pill gives, as a share of the finger's way. */
private const val EDGE_GIVE = 0.35f

/** Where a flick would have carried the finger, in seconds of its speed. */
private const val FLICK_SECONDS = 0.08f

/** The pill's place for a finger at [x], in tabs: its middle under the finger, giving a little past the ends. */
internal fun barPosition(x: Float, width: Float, tabs: Int): Float {
    val place = x / (width / tabs) - 0.5f
    val last = tabs - 1f
    return when {
        place < 0f -> place * EDGE_GIVE
        place > last -> last + (place - last) * EDGE_GIVE
        else -> place
    }
}

internal fun barTabAt(position: Float, tabs: Int): Int = position.roundToInt().coerceIn(0, tabs - 1)

/** The tab a finger let go at [x] means, a flick's speed counted in. */
internal fun barTabFor(x: Float, speed: Float, width: Float, tabs: Int): Int =
    barTabAt(barPosition(x + speed * FLICK_SECONDS, width, tabs), tabs)

/** A finger sliding along the bar: [onSlide] gets the pill's place, [onDone] the tab let go on, or null when it was called off. */
@Composable
internal fun Modifier.barSlide(tabs: Int, onSlide: (Float) -> Unit, onDone: (Int?) -> Unit): Modifier {
    val slide by rememberUpdatedState(onSlide)
    val done by rememberUpdatedState(onDone)
    return pointerInput(tabs) {
        val velocity = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            velocity.resetTracking()
            velocity.addPointerInputChange(down)
            if (!awaitSideways(down, velocity)) return@awaitEachGesture
            var released: Int? = null
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    val calledOff = change.isConsumed
                    if (calledOff) break
                    velocity.addPointerInputChange(change)
                    change.consume()
                    val width = size.width.toFloat()
                    if (!change.pressed) {
                        released = barTabFor(change.position.x, velocity.calculateVelocity().x, width, tabs)
                        break
                    }
                    slide(barPosition(change.position.x, width, tabs))
                }
            } finally {
                done(released)
            }
        }
    }
}

/**
 * Whether the finger went sideways past the slop, before anything else. Seen ahead of the tabs: one
 * held into a long press takes every later move for itself, and hold-then-slide should slide.
 */
private suspend fun AwaitPointerEventScope.awaitSideways(down: PointerInputChange, velocity: VelocityTracker): Boolean {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return false
        if (change.isConsumed || !change.pressed) return false
        velocity.addPointerInputChange(change)
        val moved = change.position - down.position
        if (abs(moved.y) > viewConfiguration.touchSlop) return false
        if (abs(moved.x) <= viewConfiguration.touchSlop) continue
        change.consume()
        return true
    }
}
