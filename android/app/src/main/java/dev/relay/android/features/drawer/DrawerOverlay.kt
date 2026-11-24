package dev.relay.android.features.drawer

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.relay.android.ui.theme.RelayTheme
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

private const val WIDTH = 0.88f
private val MAX_WIDTH = 400.dp
private val Corners = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

/**
 * Where an edge swipe may start. The screen's very edge is never the drawer's: it's the system's
 * back, and where the system says it isn't (three buttons), one-hand apps put their own back there.
 */
private val EDGE_KEPT_CLEAR = 22.dp
private val EDGE = 24.dp
private val EDGE_SLOP = 12.dp

/** The drawer over the whole screen, its scrim behind it. Composed only while some of it shows. */
@Composable
fun DrawerOverlay(state: DrawerState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!state.visible) return
    val c = RelayTheme.colors
    PredictiveBackHandler { gesture ->
        try {
            gesture.collect { state.backPeek(it.progress) }
            state.close()
        } catch (e: CancellationException) {
            state.open()
            throw e
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = minOf(maxWidth * WIDTH, MAX_WIDTH)
        val widthPx = with(LocalDensity.current) { width.toPx() }
        Box(
            Modifier.fillMaxSize().draggable(
                rememberDraggableState { state.drag(it / widthPx) },
                Orientation.Horizontal,
                onDragStopped = { state.release(it / widthPx) },
            ),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = state.progress }
                    .background(Color.Black.copy(alpha = if (c.isDark) 0.5f else 0.16f))
                    .semantics { contentDescription = "Close threads" }
                    .clickable(remember { MutableInteractionSource() }, null) { state.close() },
            )
            Box(
                Modifier.width(width).fillMaxHeight()
                    .graphicsLayer { translationX = (state.progress - 1f) * size.width }
                    .clip(Corners)
                    .background(c.chrome)
                    .semantics { paneTitle = "Threads" }
                    // Taps between its rows stay in it instead of reaching the scrim.
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
            ) { content() }
        }
    }
}

/**
 * Opens the drawer with a swipe that starts just inside the left edge. The edge itself is the
 * system's back, so it's left alone, and a swipe starting further in is a row's triage or a
 * scroll. Vertical intent wins, as it does for the rows.
 */
@Composable
fun Modifier.drawerEdgeSwipe(state: DrawerState, enabled: Boolean, screenWidth: Dp): Modifier {
    if (!enabled) return this
    val density = LocalDensity.current
    val back = WindowInsets.systemGestures.getLeft(density, androidx.compose.ui.platform.LocalLayoutDirection.current)
    val strip by rememberUpdatedState(with(density) { maxOf(back.toFloat(), EDGE_KEPT_CLEAR.toPx()).let { it..(it + EDGE.toPx()) } })
    val width by rememberUpdatedState(with(density) { minOf(screenWidth * WIDTH, MAX_WIDTH).toPx() })
    val slop = with(density) { EDGE_SLOP.toPx() }
    return pointerInput(state) {
        awaitEachGesture {
            // The initial pass: a row under the finger never sees a swipe that is the drawer's.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x !in strip) return@awaitEachGesture
            val velocity = VelocityTracker()
            var dx = 0f
            var dy = 0f
            var dragging = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                velocity.addPointerInputChange(change)
                if (!change.pressed) {
                    if (dragging) change.consume()
                    break
                }
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                if (!dragging) {
                    if (abs(dy) > slop || dx < -slop) return@awaitEachGesture
                    dragging = dx > slop
                } else {
                    state.drag(delta.x / width)
                }
                if (dragging) change.consume()
            }
            if (dragging) state.release(velocity.calculateVelocity().x / width)
        }
    }
}
