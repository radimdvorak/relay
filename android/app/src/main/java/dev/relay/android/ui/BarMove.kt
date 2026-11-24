package dev.relay.android.ui

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** What a bar icon does when its tab is picked; every move starts and ends at rest. */
enum class BarMove {
    /** A shake that dies down: speech bubbles talking. */
    Wiggle,

    /** A hop: a folder pulled out of its drawer. */
    Lift,

    /** A tug sideways and back: a branch pulled in. */
    Tug,
}

internal const val BAR_MOVE_MS = 460

/** [move] at [t], 0 to 1. */
internal fun GraphicsLayerScope.play(move: BarMove, t: Float) {
    val arc = sin(PI * t).toFloat()
    val fading = 1f - t
    val swell = 1f + 0.14f * arc
    scaleX = swell
    scaleY = swell
    when (move) {
        BarMove.Wiggle -> rotationZ = sin(3 * PI * t).toFloat() * 16f * fading
        BarMove.Lift -> {
            translationY = -5.dp.toPx() * arc
            rotationZ = -8f * arc
        }
        BarMove.Tug -> translationX = sin(2 * PI * t).toFloat() * -5.dp.toPx() * fading
    }
}
