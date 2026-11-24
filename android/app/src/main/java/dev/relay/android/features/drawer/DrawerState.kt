package dev.relay.android.features.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.relay.android.ui.theme.RelayMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How far back the drawer gives way under a back gesture before it commits. */
private const val BACK_PEEK = 0.22f

/** A drag past this much of the way, or a fling this fast (drawer widths a second), decides it. */
private const val SETTLE_AT = 0.4f
private const val FLING = 1.2f

/** The thread drawer: 0 is closed, 1 is all the way open. */
@Stable
class DrawerState(private val scope: CoroutineScope) {
    private val position = Animatable(0f).apply { updateBounds(0f, 1f) }

    /** Read it in a layer or draw lambda: it moves every frame of a drag. */
    val progress: Float get() = position.value

    /** Whether any of it shows, so it's composed and takes the touches and back. */
    val visible by derivedStateOf { position.value > 0f || position.targetValue > 0f }

    fun open() = settle(1f)

    fun close() = settle(0f)

    /** Follows a finger; [by] is in drawer widths. */
    fun drag(by: Float) {
        scope.launch { position.snapTo((position.value + by).coerceIn(0f, 1f)) }
    }

    /** The finger left; [velocity] is in drawer widths a second, opening positive. */
    fun release(velocity: Float) {
        val opens = if (kotlin.math.abs(velocity) > FLING) velocity > 0f else position.value > SETTLE_AT
        settle(if (opens) 1f else 0f, velocity)
    }

    /** A back gesture under way: the drawer gives a little with it. */
    fun backPeek(gesture: Float) {
        scope.launch { position.snapTo(1f - BACK_PEEK * gesture) }
    }

    private fun settle(to: Float, velocity: Float = 0f) {
        scope.launch { position.animateTo(to, RelayMotion.standard.slowSpatialSpec(), velocity) }
    }
}

@Composable
fun rememberDrawerState(): DrawerState {
    val scope = rememberCoroutineScope()
    return remember(scope) { DrawerState(scope) }
}
